package com.codegym.service.prescription;

import com.codegym.dto.doctor.PrescriptionForm;
import com.codegym.model.Prescription;

import java.util.List;
import java.util.Optional;

public interface IPrescriptionService {

    List<Prescription> findByAppointmentId(Long appointmentId);

    Optional<Prescription> findById(Long id);

    Prescription savePrescription(PrescriptionForm form);

    void deletePrescription(Long id);

    List<Prescription> findByDoctorId(Long doctorId);
}
