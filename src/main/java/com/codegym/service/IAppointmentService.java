package com.codegym.service;

import com.codegym.dto.AppointmentDTO;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.codegym.model.TimeSlot;
import java.util.List;

import java.time.LocalDate;

public interface IAppointmentService {
    boolean isSlotBooked(Long doctorId, java.time.LocalDate date, Long slotId);
    List<TimeSlot> findAvailableTimeSlots(Long doctorId, LocalDate date);
    Appointment createAppointment(AppointmentDTO dto);
    Appointment findByBookingCode(String bookingCode);
    Page<Appointment> findAllAppointments(AppointmentStatus status, LocalDate date,
                                          String doctorName, Pageable pageable);
    Page<Appointment> findDoctorAppointments(Long doctorId, AppointmentStatus status, LocalDate date, Pageable pageable);
    boolean updateStatus(Long appointmentId, AppointmentStatus newStatus);
}