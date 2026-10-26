package com.codegym.service;

import com.codegym.model.Doctor;
import com.codegym.model.Specialty;
import com.codegym.model.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface IDoctorService {
    List<Specialty> findAllSpecialties();
    Page<Doctor> findAllDoctors(Pageable pageable);
    Page<Doctor> findDoctorsBySpecialty(Long specialtyId, Pageable pageable);
    Page<Doctor> searchDoctors(Long specialtyId, String searchTerm, Pageable pageable);
    Doctor findDoctorById(Long id);
    List<TimeSlot> findAllTimeSlots();
}