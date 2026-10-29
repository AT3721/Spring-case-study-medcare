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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.codegym.model.TimeSlot;
import com.codegym.model.User;
import java.util.List;

import java.time.LocalDate;

public interface IAppointmentService {
    boolean isSlotBooked(Long doctorId, java.time.LocalDate date, Long slotId);
    List<TimeSlot> findAvailableTimeSlots(Long doctorId, LocalDate date);
    java.util.List<com.codegym.model.Doctor> findAvailableDoctors(Long specialtyId, LocalDate date, Long timeSlotId);
    Appointment createAppointment(AppointmentDTO dto);
    Appointment createAppointment(AppointmentDTO dto, User patientAccount);
    Appointment findByBookingCode(String bookingCode);
    Appointment findVerifiedAppointment(String bookingCode, String patientPhone);
    void cancelAppointmentByBookingCode(String bookingCode, String patientPhone);
    Page<Appointment> findAllAppointments(AppointmentStatus status, LocalDate date,
                                          Long specialtyId, String doctorName, Pageable pageable);
    List<Appointment> findAppointmentsForCalendar(LocalDate startDate, LocalDate endDate,
                                                  AppointmentStatus status, Long specialtyId);
    Page<Appointment> findDoctorAppointments(Long doctorId, AppointmentStatus status, LocalDate date, Pageable pageable);
    boolean updateStatus(Long appointmentId, AppointmentStatus newStatus);
    boolean updateClinicalResult(Long appointmentId, String clinicalResult);
    boolean updateAppointmentDetails(Long appointmentId, AppointmentStatus newStatus,
                                     String clinicalResult, boolean updateClinicalResult);
    boolean reschedulePatientAppointment(Long appointmentId, Long patientAccountId,
                                         AppointmentRescheduleForm form);
    boolean cancelPatientAppointment(Long appointmentId, Long patientAccountId);
}