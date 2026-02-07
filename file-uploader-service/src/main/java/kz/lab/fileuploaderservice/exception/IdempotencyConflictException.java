package kz.lab.fileuploaderservice.exception;

public class IdempotencyConflictException extends RuntimeException{
    public IdempotencyConflictException(String message) {
        super(message);
    }
}

