package com.codegym.service;

import com.codegym.dto.AppointmentDTO;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.model.Doctor;
import com.codegym.model.TimeSlot;
import com.codegym.model.Specialty;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.IDoctorScheduleRepository;
import com.codegym.repository.ITimeSlotRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock
    private IAppointmentRepository appointmentRepository;

    @Mock
    private IDoctorRepository doctorRepository;

    @Mock
    private ITimeSlotRepository timeSlotRepository;

    @Mock
    private IDoctorScheduleRepository doctorScheduleRepository;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    @Test
    void bookingCancelledSlotCreatesNewAppointmentWithLongCode() {
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setSpecialty(new Specialty());
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setId(2L);
        AppointmentDTO dto = validDto();
        when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndTimeSlotIdAndStatusNot(
                1L, dto.getAppointmentDate(), 2L, AppointmentStatus.CANCELLED)).thenReturn(false);
        when(timeSlotRepository.findById(2L)).thenReturn(Optional.of(timeSlot));
        when(doctorScheduleRepository.findTimeSlotIdsByDoctorAndDay(
                1L, dto.getAppointmentDate().getDayOfWeek())).thenReturn(List.of(2L));
        when(appointmentRepository.existsByBookingCode(anyString())).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment created = appointmentService.createAppointment(dto);

        assertEquals(AppointmentStatus.PENDING, created.getStatus());
        assertTrue(created.getBookingCode().matches("MC-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{16}"));
        assertSame(doctor, created.getDoctor());
        verify(doctorRepository).findByIdForUpdate(1L);
    }

    @Test
    void bookingOccupiedSlotFailsBeforeSaving() {
        AppointmentDTO dto = validDto();
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setSpecialty(new Specialty());
        when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndTimeSlotIdAndStatusNot(
                1L, dto.getAppointmentDate(), 2L, AppointmentStatus.CANCELLED)).thenReturn(true);

        assertThrows(AppointmentSlotUnavailableException.class, () -> appointmentService.createAppointment(dto));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void bookingOutsideDoctorsWeeklyScheduleIsRejected() {
        AppointmentDTO dto = validDto();
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        doctor.setSpecialty(new Specialty());
        when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndTimeSlotIdAndStatusNot(
                1L, dto.getAppointmentDate(), 2L, AppointmentStatus.CANCELLED)).thenReturn(false);
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setId(2L);
        when(timeSlotRepository.findById(2L)).thenReturn(Optional.of(timeSlot));
        when(doctorScheduleRepository.findTimeSlotIdsByDoctorAndDay(
                1L, dto.getAppointmentDate().getDayOfWeek())).thenReturn(List.of());

        InvalidAppointmentSelectionException exception = assertThrows(
                InvalidAppointmentSelectionException.class, () -> appointmentService.createAppointment(dto));

        assertEquals("timeSlotId", exception.getField());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void invalidDoctorSelectionReturnsFieldSpecificError() {
        when(doctorRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        AppointmentDTO dto = validDto();
        dto.setDoctorId(999L);

        InvalidAppointmentSelectionException exception = assertThrows(
                InvalidAppointmentSelectionException.class, () -> appointmentService.createAppointment(dto));

        assertEquals("doctorId", exception.getField());
        verifyNoInteractions(timeSlotRepository);
    }

    @Test
    void inactiveDoctorCannotBeBooked() {
        Doctor doctor = new Doctor();
        doctor.setActive(false);
        when(doctorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(doctor));

        InvalidAppointmentSelectionException exception = assertThrows(
                InvalidAppointmentSelectionException.class,
                () -> appointmentService.createAppointment(validDto()));

        assertEquals("doctorId", exception.getField());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void cancelledAppointmentCannotBeReopened() {
        Appointment appointment = new Appointment();
        appointment.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(9L)).thenReturn(Optional.of(appointment));

        assertThrows(IllegalArgumentException.class,
                () -> appointmentService.updateStatus(9L, AppointmentStatus.PENDING));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    private AppointmentDTO validDto() {
        AppointmentDTO dto = new AppointmentDTO();
        dto.setDoctorId(1L);
        dto.setTimeSlotId(2L);
        dto.setAppointmentDate(LocalDate.now().plusDays(1));
        dto.setPatientName("Test Patient");
        dto.setPatientPhone("0912345678");
        return dto;
    }
}
