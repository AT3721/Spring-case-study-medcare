package com.codegym.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void phoneValidationRequiresSupportedTenDigitNumber() {
        AppointmentDTO dto = new AppointmentDTO();
        dto.setPatientPhone("0|12345678");

        Set<String> invalidFields = validator.validate(dto).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertTrue(invalidFields.contains("patientPhone"));
    }

    @Test
    void phoneValidationAcceptsSupportedTenDigitNumber() {
        AppointmentDTO dto = new AppointmentDTO();
        dto.setPatientPhone("0912345678");

        boolean phoneIsInvalid = validator.validate(dto).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("patientPhone"));

        assertFalse(phoneIsInvalid);
    }
}
