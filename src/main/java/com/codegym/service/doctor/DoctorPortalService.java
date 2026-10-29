package com.codegym.service.doctor;

import com.codegym.dto.doctor.PrescriptionForm;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.model.Doctor;
import com.codegym.model.User;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IUserRepository;
import com.codegym.service.prescription.IPrescriptionService;
import com.codegym.model.Prescription;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DoctorPortalService {
    private final IAppointmentRepository appointmentRepository;
    private final IUserRepository userRepository;
    private final IPrescriptionService prescriptionService;

    public DoctorPortalService(IAppointmentRepository appointmentRepository,
                               IUserRepository userRepository,
                               IPrescriptionService prescriptionService) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.prescriptionService = prescriptionService;
    }

    @Transactional(readOnly = true)
    public Doctor findDoctor(String username) {
        return findDoctorUser(username).getDoctor();
    }

    @Transactional(readOnly = true)
    public List<Appointment> findAppointments(String username, AppointmentStatus status,
                                              java.time.LocalDate date) {
        Doctor doctor = findDoctor(username);
        return doctor == null ? List.of()
                : appointmentRepository.findDoctorAppointments(doctor.getId(), status, date);
    }

    @Transactional(readOnly = true)
    public Appointment findAppointment(Long appointmentId, String username) {
        Doctor doctor = findDoctor(username);
        if (doctor == null) {
            return null;
        }
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        return appointment != null && appointment.getDoctor().getId().equals(doctor.getId()) ? appointment : null;
    }

    @Transactional(readOnly = true)
    public List<Prescription> findPrescriptions(Long appointmentId) {
        return prescriptionService.findByAppointmentId(appointmentId);
    }

    @Transactional
    public boolean updateClinicalResult(Long appointmentId, String username,
                                        String clinicalResult, boolean markCompleted) {
        Appointment appointment = findAppointment(appointmentId, username);
        if (appointment == null) {
            return false;
        }
        appointment.setClinicalResult(clinicalResult == null ? null : clinicalResult.trim());
        if (markCompleted && appointment.getStatus() != AppointmentStatus.CANCELLED) {
            appointment.setStatus(AppointmentStatus.COMPLETED);
        }
        appointmentRepository.save(appointment);
        return true;
    }

    @Transactional
    public void savePrescription(Long appointmentId, String username, PrescriptionForm form) {
        if (findAppointment(appointmentId, username) == null) {
            throw new IllegalArgumentException("Không tìm thấy lịch khám.");
        }
        form.setAppointmentId(appointmentId);
        prescriptionService.savePrescription(form);
    }

    @Transactional
    public void deletePrescription(Long appointmentId, Long prescriptionId, String username) {
        if (findAppointment(appointmentId, username) == null) {
            throw new IllegalArgumentException("Không có quyền thao tác.");
        }
        prescriptionService.deletePrescription(prescriptionId);
    }

    private User findDoctorUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin tài khoản bác sĩ."));
    }
}
