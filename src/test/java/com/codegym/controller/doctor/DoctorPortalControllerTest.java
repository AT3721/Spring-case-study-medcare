package com.codegym.controller.doctor;

import com.codegym.dto.doctor.PrescriptionForm;
import com.codegym.dto.doctor.DoctorPortalFilterForm;
import com.codegym.dto.doctor.DoctorDirectoryForm;
import com.codegym.model.*;
import com.codegym.service.doctor.DoctorPortalService;
import com.codegym.dto.doctor.PrescriptionForm;
import com.codegym.dto.doctor.DoctorPortalFilterForm;
import com.codegym.dto.doctor.DoctorDirectoryForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorPortalControllerTest {

    @Mock
    private DoctorPortalService doctorPortalService;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private DoctorPortalController controller;

    private User createDoctorUser() {
        Doctor doc = new Doctor();
        doc.setId(1L);
        doc.setName("BS. Nguyễn Thanh Tùng");
        Specialty sp = new Specialty();
        sp.setName("Tai Mũi Họng");
        doc.setSpecialty(sp);

        User user = new User();
        user.setUsername("doctor1");
        user.setDoctor(doc);
        return user;
    }

    @Test
    void showDoctorAppointmentsReturnsAppointmentsView() {
        User doctorUser = createDoctorUser();
        when(userDetails.getUsername()).thenReturn("doctor1");
        when(doctorPortalService.findDoctor("doctor1")).thenReturn(doctorUser.getDoctor());
        when(doctorPortalService.findAppointments("doctor1", null, null)).thenReturn(List.of());

        Model model = new ConcurrentModel();
        String view = controller.showDoctorAppointments(new DoctorPortalFilterForm(), userDetails, model);

        assertEquals("doctor-portal/appointments", view);
        assertTrue(model.containsAttribute("doctor"));
        assertTrue(model.containsAttribute("appointments"));
    }

    @Test
    void showAppointmentDetailWithMatchingDoctorReturnsDetailView() {
        User doctorUser = createDoctorUser();
        when(userDetails.getUsername()).thenReturn("doctor1");
        Appointment app = new Appointment();
        app.setId(10L);
        app.setDoctor(doctorUser.getDoctor());

        when(doctorPortalService.findDoctor("doctor1")).thenReturn(doctorUser.getDoctor());
        when(doctorPortalService.findAppointment(10L, "doctor1")).thenReturn(app);
        when(doctorPortalService.findPrescriptions(10L)).thenReturn(List.of());

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = controller.showAppointmentDetail(10L, userDetails, model, ra);

        assertEquals("doctor-portal/appointment-detail", view);
        assertEquals(app, model.getAttribute("appointment"));
    }

    @Test
    void savePrescriptionValidDataSavesAndRedirects() {
        User doctorUser = createDoctorUser();
        when(userDetails.getUsername()).thenReturn("doctor1");
        Appointment app = new Appointment();
        app.setId(10L);
        app.setDoctor(doctorUser.getDoctor());
        when(doctorPortalService.findAppointment(10L, "doctor1")).thenReturn(app);

        PrescriptionForm form = new PrescriptionForm();
        form.setMedicineName("Paracetamol");
        form.setDosage("500mg");
        form.setQuantity("10 viên");

        BindingResult br = new BeanPropertyBindingResult(form, "prescriptionForm");
        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();

        String view = controller.savePrescription(10L, form, br, userDetails, model, ra);

        assertEquals("redirect:/doctor/appointments/10", view);
        verify(doctorPortalService).savePrescription(10L, "doctor1", form);
    }
}
