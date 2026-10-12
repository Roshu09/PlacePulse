package com.placepulse.drive.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> errors;

    public ApiException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    /** `errors` maps a field name to what is wrong with it, so forms can show it next to the field. */
    public ApiException(HttpStatus status, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
