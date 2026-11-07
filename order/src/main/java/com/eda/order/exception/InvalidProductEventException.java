package com.eda.order.exception;

public class InvalidProductEventException extends RuntimeException {

    public InvalidProductEventException(String message) {
        super(message);
    }

    public InvalidProductEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
