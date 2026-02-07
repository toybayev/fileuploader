package kz.lab.fileuploaderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import kz.lab.fileuploaderservice.model.entity.FileEntity;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileInfoResponse {

    @JsonProperty("file_id")
    private Long fileId;

    @JsonProperty("original_filename")
    private String originalFilename;

    @JsonProperty("file_size")
    private Long fileSize;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("uploaded_at")
    private LocalDateTime uploadedAt;

    public static FileInfoResponse from(FileEntity entity) {
        FileInfoResponse response = new FileInfoResponse();
        response.setFileId(entity.getId());
        response.setOriginalFilename(entity.getOriginalFilename());
        response.setFileSize(entity.getFileSize());
        response.setContentType(entity.getContentType());
        response.setUploadedAt(entity.getUploadedAt());
        return response;
    }

}
