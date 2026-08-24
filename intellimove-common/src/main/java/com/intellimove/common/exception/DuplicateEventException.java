package com.intellimove.common.exception;

public class DuplicateEventException extends RuntimeException {

    public DuplicateEventException(String eventId) {
        super(String.format("Duplicate event detected: %s", eventId));
    }
}
