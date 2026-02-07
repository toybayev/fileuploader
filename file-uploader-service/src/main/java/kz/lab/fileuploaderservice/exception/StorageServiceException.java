package kz.lab.fileuploaderservice.exception;

public class StorageServiceException extends RuntimeException{
    public StorageServiceException(String message) {
        super(message);
    }

    public StorageServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

