package kz.lab.fileuploaderservice.service;

import kz.lab.fileuploaderservice.dto.FileInfoResponse;
import kz.lab.fileuploaderservice.exception.ResourceNotFoundException;
import kz.lab.fileuploaderservice.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileRepository fileRepository;
    private final MinioService minioService;


    public Flux<FileInfoResponse> getUserFiles(Long userId, int page, int size){
        log.info("Fetching files for user: {}, page: {}, size: {}", userId, page, size);

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "uploadedAt")
        );

        return fileRepository.findByUserId(userId, pageRequest)
                .map(FileInfoResponse::from)
                .doOnComplete(() ->
                        log.info("Fetched files for user: {}", userId)
                );
    }

    public Mono<FileInfoResponse> getFileInfo(Long fileId, Long userId){
        log.info("Fetching file info: fileId={}, userId={}", fileId, userId);

        return fileRepository.findByIdAndUserId(fileId, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        String.format("File not found: id=%d", fileId)
                )))
                .map(FileInfoResponse::from)
                .doOnSuccess(response ->
                        log.info("Found file: id={}, name={}", fileId, response.getOriginalFilename())
                );
    }

    public Mono<String> getDownloadUrl(Long fileId, Long userId){
        log.info("Generating download URL: fileId={}, userId={}", fileId, userId);

        return fileRepository.findByIdAndUserId(fileId, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        String.format("File not found: id=%d", fileId)
                )))
                .flatMap(file ->
                    minioService.generatePresignedDownloadUrl(file.getStoredFilename())
                )
                .doOnSuccess(url ->
                        log.info("Generated download URL for file: {}", fileId)
                );
    }

    public Mono<Void> deleteFile(Long fileId, Long userId){
        log.info("Deleting file: fileId={}, userId={}", fileId, userId);

        return fileRepository.findByIdAndUserId(fileId, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        String.format("File not found: id=%d", fileId)
                )))
                .flatMap(file -> {
                    String storedFilename = file.getStoredFilename();
                    return minioService.deleteFile(storedFilename)
                            .doOnSuccess(v ->
                                    log.info("Deleted file from MinIO: {}", storedFilename)
                            )
                            .then(fileRepository.deleteById(fileId))
                            .doOnSuccess(v ->
                                    log.info("Deleted file from database: fileId={}", fileId)
                            );
                })
                .then()
                .doOnSuccess(v ->
                        log.info("File deleted successfully: fileId={}", fileId)
                )
                .onErrorResume(e -> {
                    log.error("Failed to delete file: fileId={}", fileId, e);
                    return Mono.error(e);
                });
    }

    public Mono<Long> getUserFilesCount(Long userId) {
        log.debug("Counting files for user: {}", userId);

        return fileRepository.countByUserId(userId)
                .doOnSuccess(count ->
                        log.debug("User {} has {} files", userId, count)
                );
    }

}
