package kz.lab.fileuploaderservice.controller;

import kz.lab.fileuploaderservice.dto.FileInfoResponse;
import kz.lab.fileuploaderservice.dto.FileUploadResponse;
import kz.lab.fileuploaderservice.service.FileService;
import kz.lab.fileuploaderservice.service.FileUploadService;
import kz.lab.fileuploaderservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/files")
@Slf4j
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final FileService fileService;


    @PostMapping(value = "/upload",
                consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<FileUploadResponse> uploadFile(
            @RequestPart("file") Mono<FilePart> filePartMono,
            @RequestHeader("X-Idempotency-Key") String idempotencyKeyHeader){
        log.info("Received file upload request with idempotency-key: {}", idempotencyKeyHeader);

        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyHeader);
        } catch (IllegalArgumentException e) {
            log.error("Invalid idempotency-key format: {}", idempotencyKeyHeader);
            return Mono.error(new IllegalArgumentException(
                    "X-Idempotency-Key must be a valid UUID"
            ));
        }


        return SecurityUtils.getCurrentUserId()
                .flatMap(userId ->
                        fileUploadService.uploadFile(filePartMono, userId, idempotencyKey)
                );

    }


    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<FileInfoResponse> listFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Listing files: page={}, size={}", page, size);


        if (page < 0) {
            return Flux.error(new IllegalArgumentException("Page must be >= 0"));
        }
        if (size < 1 || size > 100) {
            return Flux.error(new IllegalArgumentException("Size must be between 1 and 100"));
        }

        return SecurityUtils.getCurrentUserId()
                .flatMapMany(userId ->
                        fileService.getUserFiles(userId, page, size)
                );
    }



    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<FileInfoResponse> getFileInfo(@PathVariable Long id) {
        log.info("Getting file info: id={}", id);



        return SecurityUtils.getCurrentUserId()
                .flatMap(userId ->
                        fileService.getFileInfo(id, userId)
                );
    }


    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, String>>> getDownloadUrl(@PathVariable Long id) {
        log.info("Generating download URL for file: id={}", id);

        return SecurityUtils.getCurrentUserId()
                .flatMap(userId ->
                        fileService.getDownloadUrl(id, userId)
                )
                .map(url -> ResponseEntity.ok(Map.of("download_url", url)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteFile(@PathVariable Long id) {
        log.info("Deleting file: id={}", id);


        return SecurityUtils.getCurrentUserId()
                .flatMap(userId ->
                        fileService.deleteFile(id, userId)
                );
    }

}


//
