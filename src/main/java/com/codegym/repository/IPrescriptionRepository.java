package com.codegym.repository;

import com.codegym.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IPrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);

    @Query("SELECT p FROM Prescription p WHERE p.appointment.doctor.id = :doctorId ORDER BY p.createdAt DESC")
    List<Prescription> findByDoctorId(@Param("doctorId") Long doctorId);

    void deleteByAppointmentId(Long appointmentId);
}
