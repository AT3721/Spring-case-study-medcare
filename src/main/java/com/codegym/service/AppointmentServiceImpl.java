package com.codegym.service;

import com.codegym.dto.AppointmentDTO;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.model.Doctor;
import com.codegym.model.TimeSlot;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.IDoctorScheduleRepository;
import com.codegym.repository.ITimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.security.SecureRandom;

@Service
public class AppointmentServiceImpl implements IAppointmentService {
    private static final String BOOKING_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BOOKING_CODE_LENGTH = 16;

    @Autowired
    private IAppointmentRepository appointmentRepository;

    @Autowired
    private IDoctorRepository doctorRepository;

    @Autowired
    private ITimeSlotRepository timeSlotRepository;

    @Autowired
    private IDoctorScheduleRepository doctorScheduleRepository;

    @Override
    public boolean isSlotBooked(Long doctorId, java.time.LocalDate date, Long slotId) {
        return appointmentRepository.existsByDoctorIdAndAppointmentDateAndTimeSlotIdAndStatusNot(
                doctorId, date, slotId, AppointmentStatus.CANCELLED);
    }

    @Override
    public java.util.List<TimeSlot> findAvailableTimeSlots(Long doctorId, LocalDate date) {
        if (doctorRepository.findActiveById(doctorId).isEmpty()
                || date == null || !date.isAfter(LocalDate.now())) {
            return java.util.List.of();
        }
        java.util.Set<Long> bookedIds = new java.util.HashSet<>(
                appointmentRepository.findBookedTimeSlotIds(doctorId, date, AppointmentStatus.CANCELLED));
        java.util.Set<Long> scheduledIds = new java.util.HashSet<>(
                doctorScheduleRepository.findTimeSlotIdsByDoctorAndDay(doctorId, date.getDayOfWeek()));
        return timeSlotRepository.findByActiveTrueOrderByStartTimeAsc().stream()
                .filter(slot -> scheduledIds.contains(slot.getId()))
                .filter(slot -> !bookedIds.contains(slot.getId()))
                .toList();
    }

    @Override
    @Transactional
    public Appointment createAppointment(AppointmentDTO dto) {
        Doctor doctor = doctorRepository.findByIdForUpdate(dto.getDoctorId())
                .orElseThrow(() -> new InvalidAppointmentSelectionException(
                        "doctorId", "Không tìm thấy Bác sĩ đã chọn"));
        if (!doctor.isActive() || doctor.getSpecialty() == null || !doctor.getSpecialty().isActive()) {
            throw new InvalidAppointmentSelectionException(
                    "doctorId", "Bác sĩ đã tạm ngừng nhận lịch. Vui lòng chọn bác sĩ khác.");
        }
        if (isSlotBooked(dto.getDoctorId(), dto.getAppointmentDate(), dto.getTimeSlotId())) {
            throw new AppointmentSlotUnavailableException();
        }
        TimeSlot timeSlot = timeSlotRepository.findById(dto.getTimeSlotId())
                .orElseThrow(() -> new InvalidAppointmentSelectionException(
                        "timeSlotId", "Không tìm thấy ca khám đã chọn"));
        if (!timeSlot.isActive()) {
            throw new InvalidAppointmentSelectionException(
                    "timeSlotId", "Khung giờ này không còn nhận đặt lịch.");
        }
        if (!doctorScheduleRepository.findTimeSlotIdsByDoctorAndDay(
                doctor.getId(), dto.getAppointmentDate().getDayOfWeek()).contains(timeSlot.getId())) {
            throw new InvalidAppointmentSelectionException(
                    "timeSlotId", "Bác sĩ không làm việc vào ngày hoặc ca khám đã chọn.");
        }

        Appointment appointment = new Appointment();
        String code = generateBookingCode();

        appointment.setBookingCode(code);
        appointment.setDoctor(doctor);
        appointment.setTimeSlot(timeSlot);
        appointment.setAppointmentDate(dto.getAppointmentDate());
        appointment.setPatientName(dto.getPatientName());
        appointment.setPatientPhone(dto.getPatientPhone());
        appointment.setPatientEmail(dto.getPatientEmail());
        appointment.setNotes(dto.getNotes());
        appointment.setStatus(AppointmentStatus.PENDING);

        return appointmentRepository.save(appointment);
    }

    private String generateBookingCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder("MC-");
            for (int i = 0; i < BOOKING_CODE_LENGTH; i++) {
                code.append(BOOKING_CODE_ALPHABET.charAt(RANDOM.nextInt(BOOKING_CODE_ALPHABET.length())));
            }
            if (!appointmentRepository.existsByBookingCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Không thể tạo mã đặt lịch duy nhất");
    }

    @Override
    public Appointment findByBookingCode(String bookingCode) {
        return appointmentRepository.findByBookingCode(bookingCode).orElse(null);
    }

    @Override
    public Page<Appointment> findAllAppointments(AppointmentStatus status, LocalDate date,
                                                 String doctorName, Pageable pageable) {
        String normalizedDoctorName = doctorName == null || doctorName.isBlank() ? null : doctorName.trim();
        return appointmentRepository.searchAppointments(status, date, normalizedDoctorName, pageable);
    }

    @Override
    public Page<Appointment> findDoctorAppointments(Long doctorId, AppointmentStatus status,
                                                   LocalDate date, Pageable pageable) {
        return appointmentRepository.searchByDoctorId(doctorId, status, date, pageable);
    }

    @Override
    public boolean updateStatus(Long appointmentId, AppointmentStatus newStatus) {
        Optional<Appointment> optional = appointmentRepository.findById(appointmentId);
        if (optional.isPresent()) {
            Appointment appointment = optional.get();
            if (!isAllowedTransition(appointment.getStatus(), newStatus)) {
                throw new IllegalArgumentException(
                        "Không thể chuyển trạng thái từ " + appointment.getStatus() + " sang " + newStatus);
            }
            appointment.setStatus(newStatus);
            appointmentRepository.save(appointment);
            return true;
        }
        return false;
    }

    private boolean isAllowedTransition(AppointmentStatus current, AppointmentStatus target) {
        if (current == target) {
            return true;
        }
        return switch (current) {
            case PENDING -> target == AppointmentStatus.CONFIRMED || target == AppointmentStatus.CANCELLED;
            case CONFIRMED -> target == AppointmentStatus.COMPLETED || target == AppointmentStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };
    }
}