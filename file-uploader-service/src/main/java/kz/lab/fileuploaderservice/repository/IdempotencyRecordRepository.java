package kz.lab.fileuploaderservice.repository;

import kz.lab.fileuploaderservice.model.entity.IdempotencyRecordEntity;
import kz.lab.fileuploaderservice.model.entity.IdempotencyStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends ReactiveCrudRepository<IdempotencyRecordEntity, Long> {


    Mono<IdempotencyRecordEntity> findByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);

    Flux<IdempotencyRecordEntity> findByUserIdAndStatus(Long userId, IdempotencyStatus status);

    @Query("SELECT * FROM idempotency_records " +
           "WHERE status = :status " +
           "AND created_at < :createdBefore " +
           "ORDER BY created_at ASC")
    Flux<IdempotencyRecordEntity> findStaleOperations(IdempotencyStatus status,
            LocalDateTime createdBefore);

    Flux<IdempotencyRecordEntity> findByFileId(Long fileId);

    Mono<Boolean> existsByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey);

    Mono<Long> countByStatus(IdempotencyStatus status);


    @Query("DELETE FROM idempotency_records " +
           "WHERE status = :status " +
           "AND created_at < :createdBefore")
    Mono<Long> deleteOldRecords(IdempotencyStatus status, LocalDateTime createdBefore);

    @Query("""
        SELECT * FROM idempotency_records 
        WHERE status = 'IN_PROGRESS' 
          AND created_at < :threshold
        ORDER BY created_at ASC
        """)
    Flux<IdempotencyRecordEntity> findStaleOperations(LocalDateTime threshold);


    @Query("""
        DELETE FROM idempotency_records 
        WHERE created_at < :threshold 
          AND status IN ('COMPLETED', 'FAILED')
        """)
    Mono<Integer> deleteOldRecords(LocalDateTime threshold);

}
