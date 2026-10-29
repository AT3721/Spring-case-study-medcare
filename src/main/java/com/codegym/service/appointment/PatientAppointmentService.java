package com.codegym.service.appointment;

import com.codegym.model.Appointment;
import com.codegym.model.Prescription;
import com.codegym.model.User;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IPrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PatientAppointmentService {
    private final IAppointmentRepository appointmentRepository;
    private final IPrescriptionRepository prescriptionRepository;

    public PatientAppointmentService(IAppointmentRepository appointmentRepository,
                                     IPrescriptionRepository prescriptionRepository) {
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
    }

    @Transactional(readOnly = true)
    public AppointmentOverview findAppointments(User patient) {
        List<Appointment> appointments = appointmentRepository
                .findByPatientAccountIdOrderByAppointmentDateDescCreatedAtDesc(patient.getId());
        Map<Long, List<Prescription>> prescriptions = new HashMap<>();
        for (Appointment appointment : appointments) {
            prescriptions.put(appointment.getId(), prescriptionRepository
                    .findByAppointmentIdOrderByCreatedAtDesc(appointment.getId()));
        }
        return new AppointmentOverview(appointments, prescriptions);
    }

    @Transactional(readOnly = true)
    public Optional<Appointment> findChangeableAppointment(Long appointmentId, Long patientId) {
        return appointmentRepository.findByIdAndPatientAccountId(appointmentId, patientId)
                .filter(appointment -> appointment.getAppointmentDate().isAfter(LocalDate.now())
                        && appointment.getPaymentStatus() != com.codegym.model.PaymentStatus.PAID
                        && (appointment.getStatus() == com.codegym.model.AppointmentStatus.PENDING
                        || appointment.getStatus() == com.codegym.model.AppointmentStatus.CONFIRMED));
    }

    public record AppointmentOverview(List<Appointment> appointments,
                                      Map<Long, List<Prescription>> prescriptionsByAppointment) { }
}
