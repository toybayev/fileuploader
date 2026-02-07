package kz.lab.fileuploaderservice.model.entity;

import io.r2dbc.postgresql.codec.Json;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("idempotency_records")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IdempotencyRecordEntity {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("idempotency_key")
    private UUID idempotencyKey;

    @Column("status")
    private IdempotencyStatus status;

    @Column("file_id")
    private Long fileId;

    @Column("response_json")
    private Json responseJson;

    @Column("error_message")
    private String errorMessage;

    @Column("saga_id")
    private UUID sagaId;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public IdempotencyRecordEntity(Long userId, UUID idempotencyKey) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void linkToSaga(UUID sagaId) {
        this.sagaId = sagaId;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCompleted(Long fileId, String responseJson) {
        this.status = IdempotencyStatus.COMPLETED;
        this.fileId = fileId;
        this.responseJson = Json.of(responseJson);
        this.updatedAt = LocalDateTime.now();
    }


    public void markFailed(String errorMessage) {
        this.status = IdempotencyStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }


    public boolean isCompleted() {
        return this.status == IdempotencyStatus.COMPLETED;
    }


    public boolean isInProgress() {
        return this.status == IdempotencyStatus.IN_PROGRESS;
    }


    public boolean isFailed() {
        return this.status == IdempotencyStatus.FAILED;
    }


    @Override
    public String toString() {
        return "IdempotencyRecordEntity{" +
               "id=" + id +
               ", userId=" + userId +
               ", idempotencyKey=" + idempotencyKey +
               ", status=" + status +
               ", fileId=" + fileId +
               ", createdAt=" + createdAt +
               ", updatedAt=" + updatedAt +
               '}';
    }

}
