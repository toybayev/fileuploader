package kz.lab.fileuploaderservice.service;

import kz.lab.fileuploaderservice.exception.StorageServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.awt.image.DataBuffer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinioService {


    private final S3AsyncClient s3AsyncClient;
    @Value("${application.minio.bucket-name}")
    private String bucketName;

    @Value("${application.minio.endpoint}")
    private String endpoint;

    @Value("${application.minio.access-key}")
    private String accessKey;

    @Value("${application.minio.secret-key}")
    private String secretKey;



    public Mono<UploadResult> uploadFile(
            Long userId,
            String originalFilename,
            String contentType,
            Flux<org.springframework.core.io.buffer.DataBuffer> dataBufferFlux) {

        String storedFilename = generateStoredFilename(userId, originalFilename);

        log.info("Uploading file to MinIO: bucket={}, key={}",
                bucketName, storedFilename);

        AtomicLong totalBytes = new AtomicLong(0);


        return DataBufferUtils.join(dataBufferFlux
                        .doOnNext(buffer -> {
                            long bytes = buffer.readableByteCount();
                            totalBytes.addAndGet(bytes);
                            log.debug("Read {} bytes, total: {}", bytes, totalBytes.get());
                        })
                )
                .flatMap(dataBuffer -> {
                    try {
                        ByteBuffer byteBuffer = dataBuffer.asByteBuffer();
                        long fileSize = totalBytes.get();

                        PutObjectRequest putRequest = PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(storedFilename)
                                .contentType(contentType)
                                .contentLength(fileSize)
                                .build();

                        AsyncRequestBody requestBody = AsyncRequestBody.fromByteBuffer(byteBuffer);

                        CompletableFuture<PutObjectResponse> future =
                                s3AsyncClient.putObject(putRequest, requestBody);

                        return Mono.fromFuture(future)
                                .doOnSuccess(response ->
                                        log.info("Successfully uploaded file to MinIO: key={}, etag={}",
                                                storedFilename, response.eTag())
                                )
                                .map(response -> new UploadResult(
                                        buildStorageUrl(storedFilename),
                                        storedFilename,
                                        fileSize
                                ))
                                .doFinally(signalType ->
                                        //  освобождаем DataBuffer
                                        DataBufferUtils.release(dataBuffer)
                                );

                    } catch (Exception e) {
                        log.error("Failed to upload file to MinIO", e);
                        return Mono.error(new StorageServiceException(
                                "Failed to upload file to storage", e
                        ));
                    }
                })
                .onErrorMap(e -> {
                    if (e instanceof StorageServiceException) {
                        return e;
                    }
                    log.error("MinIO upload error", e);
                    return new StorageServiceException("MinIO service is unavailable", e);
                });
    }


    public Mono<Void> deleteFile(String storedFilename){
        log.info("Deleting file from MinIO: bucket={}, key={}", bucketName, storedFilename);

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(storedFilename)
                .build();

        CompletableFuture<DeleteObjectResponse> future = s3AsyncClient.deleteObject(deleteRequest);

        return Mono.fromFuture(future)
                .doOnSuccess(response ->
                        log.info("Successfully deleted file from MinIO: key={}", storedFilename)
                )
                .onErrorResume(e -> {
                    if (e instanceof NoSuchKeyException){
                        log.warn("File not found in MinIO (already deleted?): {}", storedFilename);
                        return Mono.empty();
                    }
                    log.error("Failed to delete file from MinIO", e);
                    return Mono.error(new StorageServiceException(
                            "Failed to delete file from storage", e
                    ));
                })
                .then();
    }

    public Mono<String> generatePresignedDownloadUrl(String storedFilename){
        log.debug("Generating pre-signed URL for file: {}", storedFilename);

        try {

            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    accessKey,
                    secretKey
            );
            S3Presigner presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .serviceConfiguration(
                            S3Configuration.builder()
                                    .pathStyleAccessEnabled(true)
                                    .build()
                    )
                    .build();

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storedFilename)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .getObjectRequest(getRequest)
                    .signatureDuration(Duration.ofHours(1))
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            log.info("Generated pre-signed URL for file: {}", storedFilename);

            presigner.close();

            return Mono.just(url);
        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL", e);
            return Mono.error(new StorageServiceException(
                    "Failed to generate download URL", e
            ));
        }
    }



    private String generateStoredFilename(Long userId, String originalFilename) {
        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = originalFilename.substring(lastDotIndex);
        }

        String uuid = UUID.randomUUID().toString();

        // Формат: user-{userId}/{uuid}.{ext}
        return String.format("user-%d/%s%s", userId, uuid, extension);
    }

    private String buildStorageUrl(String storedFilename) {
        return String.format("%s/%s/%s", endpoint, bucketName, storedFilename);
    }

    public static class UploadResult {
        private final String storageUrl;
        private final String storedFilename;
        private final Long fileSize;

        public UploadResult(String storageUrl, String storedFilename, Long fileSize) {
            this.storageUrl = storageUrl;
            this.storedFilename = storedFilename;
            this.fileSize = fileSize;
        }

        public String getStorageUrl() { return storageUrl; }
        public String getStoredFilename() { return storedFilename; }
        public Long getFileSize() { return fileSize; }
    }
}
