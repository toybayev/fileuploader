package kz.lab.fileuploaderservice.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileUploadResponse {


    @JsonProperty("file_id")
    private Long fileId;

    @JsonProperty("original_filename")
    private String originalFilename;


    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("download_url")
    private String downloadUrl;

    @JsonProperty("uploaded_at")
    private LocalDateTime uploadedAt;

    @JsonProperty("message")
    private String message;

    public FileUploadResponse(Long fileId, String originalFilename, Long fileSize, String contentType, String downloadUrl, LocalDateTime uploadedAt) {
        this.fileId = fileId;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.downloadUrl = downloadUrl;
        this.uploadedAt = uploadedAt;
    }
}
