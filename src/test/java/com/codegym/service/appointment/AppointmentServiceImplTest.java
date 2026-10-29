package com.codegym.service.appointment;

import com.codegym.dto.appointment.AppointmentDTO;
import com.codegym.dto.appointment.AppointmentRescheduleForm;
import com.codegym.dto.appointment.AppointmentQueryForm;
import com.codegym.dto.appointment.AdminAppointmentForm;
import com.codegym.dto.appointment.AppointmentDTO;
import com.codegym.dto.appointment.AppointmentRescheduleForm;
import com.codegym.dto.appointment.AppointmentQueryForm;
import com.codegym.dto.appointment.AdminAppointmentForm;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.model.Doctor;
import com.codegym.model.DoctorWorkShift;
import com.codegym.model.TimeSlot;
import com.codegym.model.Specialty;
import com.codegym.model.Role;
import com.codegym.model.RoleName;
import com.codegym.model.User;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.IDoctorScheduleRepository;
import com.codegym.repository.IDoctorDateScheduleRepository;
import com.codegym.repository.ITimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Mock
    private IDoctorDateScheduleRepository doctorDateScheduleRepository;

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

        User patient = new User("patient@example.com", "hash", "Patient Name");
        patient.setRoles(Set.of(new Role(RoleName.ROLE_PATIENT)));
        Appointment created = appointmentService.createAppointment(dto, patient);

        assertEquals(AppointmentStatus.PENDING, created.getStatus());
        assertTrue(created.getBookingCode().matches("MC-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{16}"));
        assertSame(doctor, created.getDoctor());
        assertSame(patient, created.getPatientAccount());
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
    void dateSpecificMorningShiftOnlyOpensMorningSlotsAndOverridesWeeklySchedule() {
        LocalDate date = LocalDate.now().plusDays(1);
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        TimeSlot morningSlot = new TimeSlot();
        morningSlot.setId(1L);
        morningSlot.setStartTime(LocalTime.of(8, 0));
        TimeSlot afternoonSlot = new TimeSlot();
        afternoonSlot.setId(2L);
        afternoonSlot.setStartTime(LocalTime.of(13, 0));
        when(doctorRepository.findActiveById(1L)).thenReturn(Optional.of(doctor));
        when(doctorDateScheduleRepository.findShiftsByDoctorAndDate(1L, date))
                .thenReturn(List.of(DoctorWorkShift.MORNING));
        when(appointmentRepository.findBookedTimeSlotIds(1L, date, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());
        when(timeSlotRepository.findByActiveTrueOrderByStartTimeAsc())
                .thenReturn(List.of(morningSlot, afternoonSlot));

        assertEquals(List.of(morningSlot), appointmentService.findAvailableTimeSlots(1L, date));
        verify(doctorScheduleRepository, never()).findTimeSlotIdsByDoctorAndDay(any(), any());
    }

    @Test
    void dateSpecificOffShiftHidesAllSlotsEvenWhenWeeklyScheduleExists() {
        LocalDate date = LocalDate.now().plusDays(2);
        Doctor doctor = new Doctor();
        doctor.setId(1L);
        when(doctorRepository.findActiveById(1L)).thenReturn(Optional.of(doctor));
        when(doctorDateScheduleRepository.findShiftsByDoctorAndDate(1L, date))
                .thenReturn(List.of(DoctorWorkShift.OFF));
        when(appointmentRepository.findBookedTimeSlotIds(1L, date, AppointmentStatus.CANCELLED))
                .thenReturn(List.of());
        when(timeSlotRepository.findByActiveTrueOrderByStartTimeAsc()).thenReturn(List.of());

        assertTrue(appointmentService.findAvailableTimeSlots(1L, date).isEmpty());
        verify(doctorScheduleRepository, never()).findTimeSlotIdsByDoctorAndDay(any(), any());
    }

    @Test
    void patientCanRescheduleOwnedUpcomingAppointment() {
        LocalDate newDate = LocalDate.now().plusDays(3);
        User patient = new User("patient@example.com", "hash", "Patient");
        patient.setId(8L);
        Doctor doctor = new Doctor();
        doctor.setId(2L);
        doctor.setSpecialty(new Specialty());
        TimeSlot newSlot = new TimeSlot();
        newSlot.setId(3L);
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(LocalDate.now().plusDays(1));
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setPatientAccount(patient);
        AppointmentRescheduleForm form = new AppointmentRescheduleForm();
        form.setDoctorId(2L);
        form.setAppointmentDate(newDate);
        form.setTimeSlotId(3L);
        when(appointmentRepository.findByIdAndPatientAccountId(5L, 8L)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(doctor));
        when(timeSlotRepository.findById(3L)).thenReturn(Optional.of(newSlot));
        when(doctorScheduleRepository.findTimeSlotIdsByDoctorAndDay(2L, newDate.getDayOfWeek()))
                .thenReturn(List.of(3L));
        when(appointmentRepository.existsByDoctorIdAndAppointmentDateAndTimeSlotIdAndIdNotAndStatusNot(
                2L, newDate, 3L, 5L, AppointmentStatus.CANCELLED)).thenReturn(false);

        assertTrue(appointmentService.reschedulePatientAppointment(5L, 8L, form));
        assertSame(doctor, appointment.getDoctor());
        assertSame(newSlot, appointment.getTimeSlot());
        assertEquals(newDate, appointment.getAppointmentDate());
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void patientCannotRescheduleAnotherAccountsAppointment() {
        AppointmentRescheduleForm form = new AppointmentRescheduleForm();
        when(appointmentRepository.findByIdAndPatientAccountId(5L, 8L)).thenReturn(Optional.empty());

        assertFalse(appointmentService.reschedulePatientAppointment(5L, 8L, form));
        verifyNoInteractions(doctorRepository, timeSlotRepository);
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

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> appointmentService.updateStatus(9L, AppointmentStatus.PENDING));
        assertEquals("Không thể chuyển từ \"Đã hủy\" sang \"Chờ xác nhận\". "
                + "Lịch này không thể chuyển sang trạng thái khác.", exception.getMessage());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void sharedAppointmentSaveCanUpdateStatusAndResultTogether() {
        Appointment appointment = new Appointment();
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(11L)).thenReturn(Optional.of(appointment));

        assertTrue(appointmentService.updateAppointmentDetails(
                11L, AppointmentStatus.COMPLETED, "Kết quả khám", true));

        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
        assertEquals("Kết quả khám", appointment.getClinicalResult());
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void clinicalResultsCanOnlyBeSavedForCompletedAppointments() {
        Appointment appointment = new Appointment();
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findById(4L)).thenReturn(Optional.of(appointment));

        assertThrows(IllegalArgumentException.class,
                () -> appointmentService.updateClinicalResult(4L, "Kết quả"));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void cancelPaidAppointmentThrowsException() {
        Appointment appointment = new Appointment();
        appointment.setId(100L);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setPaymentStatus(com.codegym.model.PaymentStatus.PAID);
        appointment.setAppointmentDate(LocalDate.now().plusDays(2));
        when(appointmentRepository.findByIdAndPatientAccountId(100L, 1L)).thenReturn(Optional.of(appointment));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> appointmentService.cancelPatientAppointment(100L, 1L));
        assertTrue(ex.getMessage().contains("đã thanh toán không thể tự hủy"));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void isPatientAppointmentChangeableReturnsFalseForPaidAppointment() {
        Appointment appointment = new Appointment();
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setPaymentStatus(com.codegym.model.PaymentStatus.PAID);
        appointment.setAppointmentDate(LocalDate.now().plusDays(2));

        assertFalse(appointmentService.isPatientAppointmentChangeable(appointment));
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
