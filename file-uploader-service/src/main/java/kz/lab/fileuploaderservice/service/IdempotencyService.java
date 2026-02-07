package kz.lab.fileuploaderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import kz.lab.fileuploaderservice.dto.FileUploadResponse;
import kz.lab.fileuploaderservice.exception.IdempotencyConflictException;
import kz.lab.fileuploaderservice.model.entity.IdempotencyRecordEntity;
import kz.lab.fileuploaderservice.model.entity.IdempotencyStatus;
import kz.lab.fileuploaderservice.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ReactiveRedisTemplate<String,String> reactiveRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${application.idempotency.redis-ttl:86400}")
    private long redisTtl;


    public Mono<FileUploadResponse> checkRedisCache(Long userId, UUID idempotencyKey){
        String redisKey = buildRedisKey(userId, idempotencyKey);

        log.debug("Checking Redis cache for key: {}", redisKey);

        return reactiveRedisTemplate.opsForValue()
                .get(redisKey)
                .flatMap(cachedJson -> {
                    log.info("Found cached result in Redis for key: {}", redisKey);

                    try {
                        FileUploadResponse response = objectMapper.readValue(
                                cachedJson,
                                FileUploadResponse.class);

                        return Mono.just(response);
                    } catch (JsonProcessingException e){
                        log.error("Failed to deserialize cached response from Redis", e);

                        return reactiveRedisTemplate.delete(redisKey)
                                .then(Mono.empty());
                    }
                })
                .doOnNext(response ->
                        log.info("Returning cached response for user: {}, key: {}", userId, idempotencyKey)
                );
    }

    public Mono<FileUploadResponse> checkAndReserveIdempotency(Long userId, UUID idempotencyKey){
        log.debug("Checking PostgreSQL for idempotency: user={}, key={}", userId, idempotencyKey);

        return idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .flatMap(existingRecord -> {
                    log.info("Found existing idempotency record: status={}", existingRecord.getStatus());

                    switch (existingRecord.getStatus()){
                        case COMPLETED:
                            log.info("Operation already completed for key: {}", idempotencyKey);

                            return deserializeResponse(existingRecord.getResponseJson())
                                    .flatMap(response ->
                                            // Кешируем ответ в редис
                                            cacheResponse(userId, idempotencyKey, response)
                                                    .thenReturn(response)
                                    );
                        case IN_PROGRESS:
                            log.warn("Concurrent request detected for key: {}", idempotencyKey);
                            return Mono.error(new IdempotencyConflictException(
                                    "Request with this idempotency key is already being processed. " +
                                    "Please wait for the original request to complete or retry later."
                            ));

                        case FAILED:
                            log.info("Previous attempt failed for key: {}, allowing retry", idempotencyKey);

                            existingRecord.setStatus(IdempotencyStatus.IN_PROGRESS);
                            existingRecord.setErrorMessage(null);

                            return idempotencyRecordRepository.save(existingRecord)
                                    .then(Mono.empty());

                        default:
                            return Mono.error(new IllegalStateException(
                                    "Unknown idempotency status: " + existingRecord.getStatus()
                            ));
                    }
                })
                .switchIfEmpty(
                        createNewIdempotencyRecord(userId, idempotencyKey)
                                .then(Mono.empty())
                );


    }


    public Mono<Void> saveCompletedOperation(
            Long userId,
            UUID idempotencyKey,
            Long fileId,
            FileUploadResponse response) {
        log.info("Saving completed operation: user={}, key={}, fileId={}",
                userId, idempotencyKey, fileId);

        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response", e);
            return Mono.error(e);
        }

        return idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .flatMap(record -> {
                    record.markCompleted(fileId, responseJson);
                    return idempotencyRecordRepository.save(record);
                })
                .flatMap(savedRecord ->
                    cacheResponse(userId, idempotencyKey, response)
                )
                .doOnSuccess(v ->
                        log.info("Successfully saved completed operation for key: {}", idempotencyKey)
                )
                .then();
    }


    public Mono<Void> saveFailedOperation(
            Long userId,
            UUID idempotencyKey,
            String errorMessage) {

        log.warn("Saving failed operation: user={}, key={}, error={}",
                userId, idempotencyKey, errorMessage);

        return idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .flatMap(record -> {
                    record.markFailed(errorMessage);
                    return idempotencyRecordRepository.save(record);
                })
                .doOnSuccess(v ->
                        log.info("Successfully saved failed operation for key: {}", idempotencyKey)
                )
                .then();
    }




    private Mono<IdempotencyRecordEntity> createNewIdempotencyRecord(Long userId, UUID idempotencyKey) {
        log.info("Creating new idempotency record: user={}, key={}", userId, idempotencyKey);

        IdempotencyRecordEntity record = new IdempotencyRecordEntity(userId, idempotencyKey);

        return idempotencyRecordRepository.save(record)
                .doOnSuccess(saved ->
                        log.info("Created idempotency record with status IN_PROGRESS: id={}", saved.getId())
                )
                .onErrorResume(DataIntegrityViolationException.class, e -> {
                    log.warn("Race condition detected while creating idempotency record", e);

                    return idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                            .flatMap(existingRecord -> {
                                if (existingRecord.isInProgress()){
                                    return Mono.error(new IdempotencyConflictException(
                                            "Concurrent request detected"
                                    ));
                                }
                                return Mono.just(existingRecord);
                            });
                });
    }

    private Mono<Void> cacheResponse(Long userId, UUID idempotencyKey, FileUploadResponse response) {
        String redisKey = buildRedisKey(userId, idempotencyKey);

        try {
            String responseJson = objectMapper.writeValueAsString(response);
            log.debug("Caching response in Redis: key={}", redisKey);

            return reactiveRedisTemplate.opsForValue()
                    .set(redisKey, responseJson, Duration.ofSeconds(redisTtl))
                    .doOnSuccess(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            log.info("Successfully cached response in Redis for key: {}", redisKey);
                        } else {
                            log.warn("Failed to cache response in Redis for key: {}", redisKey);
                        }
                    })
                    .then();
        } catch (JsonProcessingException e){
            log.error("Failed to serialize response for Redis cache", e);
            return Mono.empty();
        }
    }

    private Mono<FileUploadResponse> deserializeResponse(Json responseJson) {  // ← ИЗМЕНЕНО!
        try {
            String jsonString = responseJson.asString();

            FileUploadResponse response = objectMapper.readValue(
                    jsonString,
                    FileUploadResponse.class
            );
            return Mono.just(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize response JSON", e);
            return Mono.error(e);
        }
    }


    private String buildRedisKey(Long userId, UUID idempotencyKey) {
        return String.format("idempotency:%d:%s", userId, idempotencyKey);
    }


}
