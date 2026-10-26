package com.codegym.repository;

import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

@Repository
public interface IAppointmentRepository extends JpaRepository<Appointment, Long> {

    boolean existsByDoctorIdAndAppointmentDateAndTimeSlotIdAndStatusNot(
            Long doctorId, LocalDate appointmentDate, Long timeSlotId, AppointmentStatus status);

    boolean existsByBookingCode(String bookingCode);

    @org.springframework.data.jpa.repository.Query("""
            select a.timeSlot.id from Appointment a
            where a.doctor.id = :doctorId
              and a.appointmentDate = :date
              and a.status <> :cancelled
            """)
    List<Long> findBookedTimeSlotIds(Long doctorId, LocalDate date, AppointmentStatus cancelled);

    @org.springframework.data.jpa.repository.Query("""
            select a from Appointment a
            where a.doctor.id = :doctorId
              and (:status is null or a.status = :status)
              and (:date is null or a.appointmentDate = :date)
            """)
    Page<Appointment> searchByDoctorId(Long doctorId, AppointmentStatus status, LocalDate date, Pageable pageable);

    Optional<Appointment> findByBookingCode(String bookingCode);

    // Lọc theo trạng thái
    Page<Appointment> findByStatus(AppointmentStatus status, Pageable pageable);

    // Lọc theo ngày khám
    Page<Appointment> findByAppointmentDate(LocalDate date, Pageable pageable);

    // Lọc cả theo trạng thái lẫn ngày khám
    Page<Appointment> findByStatusAndAppointmentDate(AppointmentStatus status, LocalDate date, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select a from Appointment a
            where (:status is null or a.status = :status)
              and (:date is null or a.appointmentDate = :date)
              and (:doctorName is null or lower(a.doctor.name) like lower(concat('%', :doctorName, '%')))
            """)
    Page<Appointment> searchAppointments(AppointmentStatus status, LocalDate date,
                                         String doctorName, Pageable pageable);
}