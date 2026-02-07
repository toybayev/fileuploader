package kz.lab.fileuploaderservice.repository;

import kz.lab.fileuploaderservice.model.entity.FileEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface FileRepository extends ReactiveCrudRepository<FileEntity, Long> {


    Flux<FileEntity> findByUserIdOrderByUploadedAtDesc(Long userId);

    Flux<FileEntity> findByUserId(Long userId, Pageable pageable);

    Mono<FileEntity> findByStoredFilename(String storedFilename);

    Mono<FileEntity> findByIdAndUserId(Long fileId, Long userId);

    Mono<Long> countByUserId(Long userId);

    @Query("DELETE FROM files WHERE id = :fileId AND user_id = :userId")
    Mono<Long> deleteByIdAndUserId(Long fileId, Long userId);

    Mono<Boolean> existsByStoredFilename(String storedFilename);

}
