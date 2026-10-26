package com.codegym.service;

import com.codegym.model.Doctor;
import com.codegym.model.Specialty;
import com.codegym.model.TimeSlot;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.ISpecialtyRepository;
import com.codegym.repository.ITimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DoctorServiceImpl implements IDoctorService {

    @Autowired
    private IDoctorRepository doctorRepository;

    @Autowired
    private ISpecialtyRepository specialtyRepository;

    @Override
    public List<Specialty> findAllSpecialties() {
        return specialtyRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    public Page<Doctor> findAllDoctors(Pageable pageable) {
        return doctorRepository.findAllActive(pageable);
    }

    @Override
    public Page<Doctor> findDoctorsBySpecialty(Long specialtyId, Pageable pageable) {
        return doctorRepository.findBySpecialtyId(specialtyId, pageable);
    }

    @Override
    public Page<Doctor> searchDoctors(Long specialtyId, String searchTerm, Pageable pageable) {
        return doctorRepository.searchDoctors(specialtyId, searchTerm, pageable);
    }

    @Override
    public Doctor findDoctorById(Long id) {
        return doctorRepository.findActiveById(id).orElse(null);
    }

    @Autowired
    private ITimeSlotRepository timeSlotRepository;

    @Override
    public List<TimeSlot> findAllTimeSlots() {
        return timeSlotRepository.findByActiveTrueOrderByStartTimeAsc();
    }
}