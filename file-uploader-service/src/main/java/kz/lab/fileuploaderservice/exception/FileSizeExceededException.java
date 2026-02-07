package kz.lab.fileuploaderservice.exception;

public class FileSizeExceededException extends RuntimeException{

    public FileSizeExceededException(String message) {
        super(message);
    }
}
