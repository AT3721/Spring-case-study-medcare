package com.codegym.controller;

import com.codegym.model.Appointment;
import com.codegym.service.IAppointmentService;
import com.codegym.repository.IAppointmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AppointmentRestControllerTest {

    @Test
    void appointmentLookupRequiresMatchingPatientPhone() {
        IAppointmentService appointmentService = mock(IAppointmentService.class);
        IAppointmentRepository appointmentRepository = mock(IAppointmentRepository.class);
        Appointment appointment = new Appointment();
        appointment.setPatientPhone("0912345678");
        when(appointmentService.findByBookingCode("MC-EXAMPLE")).thenReturn(appointment);

        AppointmentRestController controller = new AppointmentRestController();
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        ReflectionTestUtils.setField(controller, "appointmentRepository", appointmentRepository);

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.searchAppointment("MC-EXAMPLE", "0987654321").getStatusCode());
        verifyNoInteractions(appointmentRepository);
    }
}
