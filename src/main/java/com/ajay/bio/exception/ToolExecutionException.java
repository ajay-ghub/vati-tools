package com.ajay.bio.exception;

public class ToolExecutionException extends Exception {
    public ToolExecutionException(final String message) {
        super(message);
    }

    public ToolExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ToolExecutionException(Throwable cause) {
        super(cause);
    }
}
