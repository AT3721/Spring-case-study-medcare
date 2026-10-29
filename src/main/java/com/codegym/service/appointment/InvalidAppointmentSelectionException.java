package com.codegym.service.appointment;

public class InvalidAppointmentSelectionException extends RuntimeException {
    private final String field;

    public InvalidAppointmentSelectionException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
