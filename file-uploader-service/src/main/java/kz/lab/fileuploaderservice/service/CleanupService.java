package kz.lab.fileuploaderservice.service;

import kz.lab.fileuploaderservice.model.entity.IdempotencyRecordEntity;
import kz.lab.fileuploaderservice.repository.FileRepository;
import kz.lab.fileuploaderservice.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class CleanupService {


    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final FileRepository fileRepository;
    private final MinioService minioService;


    @Value("${application.cleanup.stale-operation-timeout-minutes:5}")
    private int staleOperationTimeoutMinutes;

    @Value("${application.cleanup.old-records-retention-days:30}")
    private int oldRecordsRetentionDays;


    @Scheduled(
            fixedRate = 5,
            timeUnit = TimeUnit.MINUTES,
            initialDelay = 1
    )
    public void cleanupStaleOperations() {
        log.info("Starting cleanup of stale operations (timeout: {} minutes)",
                staleOperationTimeoutMinutes);

        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(staleOperationTimeoutMinutes);

        idempotencyRecordRepository.findStaleOperations(threshold)
                .flatMap(this::processStaleOperation)
                .collectList()
                .doOnSuccess(results -> {
                    long successCount = results.stream().filter(r -> r).count();
                    long failureCount = results.stream().filter(r -> !r).count();

                    if (successCount > 0 || failureCount > 0) {
                        log.info("Cleanup completed: {} operations cleaned, {} failures",
                                successCount, failureCount);
                    } else {
                        log.debug("No stale operations found");
                    }
                })
                .doOnError(e ->
                        log.error("Error during stale operations cleanup", e)
                )
                .subscribe();  // Fire-and-forget (background task)
    }

    private Mono<Boolean> processStaleOperation(IdempotencyRecordEntity record) {
        log.warn("Found stale operation: userId={}, idempotencyKey={}, age={} minutes",
                record.getUserId(),
                record.getIdempotencyKey(),
                java.time.Duration.between(record.getCreatedAt(), LocalDateTime.now()).toMinutes()
        );

        record.markFailed("Operation timeout - cleaned up by background job");

        return idempotencyRecordRepository.save(record)
                .doOnSuccess(v ->
                        log.info("Marked stale operation as FAILED: userId={}, idempotencyKey={}",
                                record.getUserId(), record.getIdempotencyKey())
                )
//                start deleting file
                .then(Mono.defer(() -> {
                    if (record.getFileId() == null) {
                        log.debug("No file_id in stale operation, skipping file cleanup");
                        return Mono.just(true);
                    }

                    Long fileId = record.getFileId();
//                  find file
                    return fileRepository.findById(fileId)
                            .flatMap(fileEntity -> {
                                String storedFilename = fileEntity.getStoredFilename();

                                log.info("Deleting orphaned file: fileId={}, filename={}",
                                        fileId, storedFilename);

                                return minioService.deleteFile(storedFilename)
                                        .doOnSuccess(v ->
                                                log.info("Deleted file from MinIO: {}", storedFilename)
                                        )
                                        .onErrorResume(e -> {
                                            log.warn("Failed to delete from MinIO: {}", storedFilename, e);
                                            return Mono.empty();
                                        })
                                        // delete from PostgreSQL
                                        .then(fileRepository.deleteById(fileId))
                                        .doOnSuccess(v ->
                                                log.info("Deleted orphaned file from DB: fileId={}", fileId)
                                        )
                                        .thenReturn(true);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("File not found in DB: fileId={} (already deleted?)", fileId);
                                return Mono.just(true);
                            }));
                }))
                .onErrorResume(e -> {
                    log.error("Failed to process stale operation: userId={}, idempotencyKey={}",
                            record.getUserId(), record.getIdempotencyKey(), e);
                    return Mono.just(false);
                });
    }

    @Scheduled(cron = "0 0 0 * * *")  // 00.00 everyday
    public void cleanupOldRecords() {
        log.info("Starting cleanup of old records (retention: {} days)",
                oldRecordsRetentionDays);

        LocalDateTime threshold = LocalDateTime.now()
                .minusDays(oldRecordsRetentionDays);

        idempotencyRecordRepository.deleteOldRecords(threshold)
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Deleted {} old idempotency records", count);
                    } else {
                        log.debug("No old records to delete");
                    }
                })
                .doOnError(e ->
                        log.error("Error during old records cleanup", e)
                )
                .subscribe();
    }

    public Mono<Long> manualCleanupStaleOperations() {
        log.info("Manual cleanup triggered");

        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(staleOperationTimeoutMinutes);

        return idempotencyRecordRepository.findStaleOperations(threshold)
                .flatMap(this::processStaleOperation)
                .filter(result -> result)
                .count()
                .doOnSuccess(count ->
                        log.info("Manual cleanup completed: {} operations cleaned", count)
                );
    }

    public Mono<Long> getStaleOperationsCount() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(staleOperationTimeoutMinutes);

        return idempotencyRecordRepository.findStaleOperations(threshold)
                .count();
    }

}
