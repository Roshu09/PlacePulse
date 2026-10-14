package com.placepulse.notify.events;

/**
 * The message can never be processed (broken JSON, missing field). Retrying would not help, so it is
 * sent straight to the dead-letter topic instead.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
