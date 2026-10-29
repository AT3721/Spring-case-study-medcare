package com.codegym.controller.appointment;

import com.codegym.model.Appointment;
import com.codegym.dto.appointment.AppointmentQueryForm;
import com.codegym.service.appointment.IAppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AppointmentRestControllerTest {

    @Test
    void appointmentLookupRequiresMatchingPatientPhone() {
        IAppointmentService appointmentService = mock(IAppointmentService.class);
        when(appointmentService.findVerifiedAppointment("MC-EXAMPLE", "0987654321")).thenReturn(null);

        AppointmentRestController controller = new AppointmentRestController();
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        AppointmentQueryForm query = new AppointmentQueryForm();
        query.setBookingCode("MC-EXAMPLE");
        query.setPatientPhone("0987654321");

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.searchAppointment(query).getStatusCode());
        verify(appointmentService).findVerifiedAppointment("MC-EXAMPLE", "0987654321");
    }
}
