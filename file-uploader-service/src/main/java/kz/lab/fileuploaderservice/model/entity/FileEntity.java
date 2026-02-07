package kz.lab.fileuploaderservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("files")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileEntity {


    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("original_filename")
    private String originalFilename;

    @Column("stored_filename")
    private String storedFilename;

    @Column("content_type")
    private String contentType;

    @Column("file_size")
    private Long fileSize;

    @Column("storage_url")
    private String storageUrl;

    @Column("bucket_name")
    private String bucketName;

    @Column("uploaded_at")
    private LocalDateTime uploadedAt;




    @Override
    public String toString() {
        return "FileEntity{" +
               "id=" + id +
               ", userId=" + userId +
               ", originalFilename='" + originalFilename + '\'' +
               ", storedFilename='" + storedFilename + '\'' +
               ", contentType='" + contentType + '\'' +
               ", fileSize=" + fileSize +
               ", bucketName='" + bucketName + '\'' +
               ", uploadedAt=" + uploadedAt +
               '}';
    }

}
