package kz.lab.fileuploaderservice.service;

import kz.lab.fileuploaderservice.dto.FileUploadResponse;
import kz.lab.fileuploaderservice.model.entity.FileEntity;
import kz.lab.fileuploaderservice.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {


    private final IdempotencyService idempotencyService;
    private final MinioService minioService;
    private final FileRepository fileRepository;

    @Value("${application.minio.bucket-name}")
    private String bucketName;




    public Mono<FileUploadResponse> uploadFile(
            Mono<FilePart> filePartMono,
            Long userId,
            UUID idempotencyKey) {
        log.info("Starting file upload: user={}, idempotencyKey={}", userId, idempotencyKey);

        return idempotencyService.checkRedisCache(userId, idempotencyKey)
//                check redis cache
                .switchIfEmpty(
//                        check db
                        idempotencyService.checkAndReserveIdempotency(userId, idempotencyKey)
                                .switchIfEmpty(
//                                        new file
                                        performFileUpload(filePartMono, userId, idempotencyKey)
                                )
                )
                .doOnSuccess(response ->
                        log.info("File upload completed successfully: fileId={}", response.getFileId())
                )
                .onErrorResume(e -> handleUploadError(e, userId, idempotencyKey));
    }

    private Mono<FileUploadResponse> performFileUpload(Mono<FilePart> filePartMono, Long userId, UUID idempotencyKey) {

        return filePartMono.flatMap(filePart -> {
            String originalFilename = filePart.filename();
            String contentType = filePart.headers().getContentType() != null
                    ? filePart.headers().getContentType().toString()
                    : "application/octet-stream";


            return minioService.uploadFile(
                    userId,
                    originalFilename,
                    contentType,
                    filePart.content()
                )
                .flatMap(uploadResult -> {
                    FileEntity fileEntity = FileEntity.builder()
                            .userId(userId)
                            .originalFilename(originalFilename)
                            .storedFilename(uploadResult.getStoredFilename())
                            .contentType(contentType)
                            .fileSize(uploadResult.getFileSize())
                            .storageUrl(uploadResult.getStorageUrl())
                            .bucketName(bucketName)
                            .uploadedAt(LocalDateTime.now())
                            .build();

                    return fileRepository.save(fileEntity)
                            .flatMap(savedFile -> {
                                return minioService.generatePresignedDownloadUrl(savedFile.getStoredFilename())
                                        .map(downloadUrl -> {
                                            FileUploadResponse response = new FileUploadResponse(
                                                    savedFile.getId(),
                                                    savedFile.getOriginalFilename(),
                                                    savedFile.getFileSize(),
                                                    savedFile.getContentType(),
                                                    downloadUrl,
                                                    savedFile.getUploadedAt());

                                            return new UploadResult(savedFile.getId(), response);
                                        });
                            });
                })
                .flatMap(result ->
                    idempotencyService.saveCompletedOperation(
                            userId,
                            idempotencyKey,
                            result.fileId,
                            result.response
                    )
                    .thenReturn(result.response)
                );
        });
    }

    private Mono<FileUploadResponse> handleUploadError(Throwable e, Long userId, UUID idempotencyKey) {
        log.error("File upload failed: user={}, key={}, error={}",
                userId, idempotencyKey, e.getMessage(), e);

        return idempotencyService.saveFailedOperation(
                userId,
                idempotencyKey,
                e.getMessage()
        ).then(Mono.error(e));
    }

    private String extractStoredFilename(String storageUrl) {
        int bucketIndex = storageUrl.indexOf(bucketName);
        if (bucketIndex >= 0) {
            return storageUrl.substring(bucketIndex + bucketName.length() + 1);
        }
        return storageUrl;
    }

    private static class UploadResult {
        final Long fileId;
        final FileUploadResponse response;

        UploadResult(Long fileId, FileUploadResponse response) {
            this.fileId = fileId;
            this.response = response;
        }
    }
}
