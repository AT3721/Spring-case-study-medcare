package com.codegym.service.prescription;

import com.codegym.dto.doctor.PrescriptionForm;
import com.codegym.model.Appointment;
import com.codegym.model.Prescription;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IPrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PrescriptionServiceImpl implements IPrescriptionService {

    private final IPrescriptionRepository prescriptionRepository;
    private final IAppointmentRepository appointmentRepository;

    public PrescriptionServiceImpl(IPrescriptionRepository prescriptionRepository,
                                   IAppointmentRepository appointmentRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prescription> findByAppointmentId(Long appointmentId) {
        return prescriptionRepository.findByAppointmentIdOrderByCreatedAtDesc(appointmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Prescription> findById(Long id) {
        return prescriptionRepository.findById(id);
    }

    @Override
    public Prescription savePrescription(PrescriptionForm form) {
        Appointment appointment = appointmentRepository.findById(form.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch khám với ID: " + form.getAppointmentId()));

        Prescription prescription;
        if (form.getId() != null) {
            prescription = prescriptionRepository.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn thuốc ID: " + form.getId()));
        } else {
            prescription = new Prescription();
            prescription.setAppointment(appointment);
        }

        prescription.setMedicineName(form.getMedicineName().trim());
        prescription.setDosage(form.getDosage().trim());
        prescription.setQuantity(form.getQuantity().trim());
        prescription.setUsageInstructions(form.getUsageInstructions() != null ? form.getUsageInstructions().trim() : null);
        prescription.setDoctorAdvice(form.getDoctorAdvice() != null ? form.getDoctorAdvice().trim() : null);
        prescription.setFollowUpDate(form.getFollowUpDate());

        return prescriptionRepository.save(prescription);
    }

    @Override
    public void deletePrescription(Long id) {
        prescriptionRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prescription> findByDoctorId(Long doctorId) {
        return prescriptionRepository.findByDoctorId(doctorId);
    }
}
