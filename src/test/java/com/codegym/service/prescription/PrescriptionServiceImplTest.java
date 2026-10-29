package com.codegym.service.prescription;

import com.codegym.dto.doctor.PrescriptionForm;
import com.codegym.model.Appointment;
import com.codegym.model.Prescription;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IPrescriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceImplTest {

    @Mock
    private IPrescriptionRepository prescriptionRepository;

    @Mock
    private IAppointmentRepository appointmentRepository;

    @InjectMocks
    private PrescriptionServiceImpl prescriptionService;

    @Test
    void savePrescriptionCreatesNewRecord() {
        Appointment appointment = new Appointment();
        appointment.setId(10L);

        PrescriptionForm form = new PrescriptionForm();
        form.setAppointmentId(10L);
        form.setMedicineName("Amoxicillin");
        form.setDosage("500mg");
        form.setQuantity("20 viên");
        form.setUsageInstructions("Uống 2 lần/ngày");
        form.setDoctorAdvice("Nghỉ ngơi, uống nhiều nước");
        form.setFollowUpDate(LocalDate.now().plusDays(7));

        when(appointmentRepository.findById(10L)).thenReturn(Optional.of(appointment));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> {
            Prescription p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        Prescription saved = prescriptionService.savePrescription(form);

        assertNotNull(saved);
        assertEquals(100L, saved.getId());
        assertEquals("Amoxicillin", saved.getMedicineName());
        assertEquals("500mg", saved.getDosage());
        assertEquals("20 viên", saved.getQuantity());
        verify(prescriptionRepository).save(any(Prescription.class));
    }

    @Test
    void findByAppointmentIdReturnsList() {
        Prescription p = new Prescription();
        p.setId(1L);
        when(prescriptionRepository.findByAppointmentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(p));

        List<Prescription> result = prescriptionService.findByAppointmentId(10L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void deletePrescriptionDelegatesToRepository() {
        prescriptionService.deletePrescription(5L);
        verify(prescriptionRepository).deleteById(5L);
    }
}
