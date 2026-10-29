package com.codegym.service.appointment;

import com.codegym.dto.appointment.AppointmentDTO;
import com.codegym.dto.appointment.AppointmentRescheduleForm;
import com.codegym.dto.appointment.AppointmentQueryForm;
import com.codegym.dto.appointment.AdminAppointmentForm;
import com.codegym.dto.appointment.AppointmentDTO;
import com.codegym.dto.appointment.AppointmentRescheduleForm;
import com.codegym.dto.appointment.AppointmentQueryForm;
import com.codegym.dto.appointment.AdminAppointmentForm;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.model.PaymentStatus;
import com.codegym.model.Doctor;
import com.codegym.model.DoctorWorkShift;
import com.codegym.model.TimeSlot;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IDoctorDateScheduleRepository;
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

    @Autowired
    private IDoctorDateScheduleRepository doctorDateScheduleRepository;

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
        java.util.Set<Long> scheduledIds = findScheduledTimeSlotIds(doctorId, date);
        return timeSlotRepository.findByActiveTrueOrderByStartTimeAsc().stream()
                .filter(slot -> scheduledIds.contains(slot.getId()))
                .filter(slot -> !bookedIds.contains(slot.getId()))
                .toList();
    }

    @Override
    public java.util.List<Doctor> findAvailableDoctors(Long specialtyId, LocalDate date, Long timeSlotId) {
        if (specialtyId == null || date == null || !date.isAfter(LocalDate.now()) || timeSlotId == null) {
            return java.util.List.of();
        }
        return doctorRepository.findActiveBySpecialtyId(specialtyId).stream()
                .filter(doc -> findScheduledTimeSlotIds(doc.getId(), date).contains(timeSlotId))
                .filter(doc -> !isSlotBooked(doc.getId(), date, timeSlotId))
                .toList();
    }

    @Override
    @Transactional
    public Appointment createAppointment(AppointmentDTO dto) {
        return createAppointment(dto, null);
    }

    @Autowired
    private com.codegym.repository.IRoleRepository roleRepository;

    @Autowired
    private com.codegym.repository.IUserRepository userRepository;

    @Override
    @Transactional
    public Appointment createAppointment(AppointmentDTO dto, com.codegym.model.User patientAccount) {
        if (patientAccount != null) {
            dto.setPatientName(patientAccount.getFullName());
            dto.setPatientEmail(patientAccount.getEmail());
        }
        if (patientAccount != null && patientAccount.getRoles().stream()
                .noneMatch(role -> role.getName() == com.codegym.model.RoleName.ROLE_PATIENT)) {
            // If the user has other staff/doctor roles without patient role, block; otherwise attach ROLE_PATIENT
            boolean hasOtherStaffRole = patientAccount.getRoles().stream().anyMatch(r ->
                    r.getName() == com.codegym.model.RoleName.ROLE_ADMIN
                            || r.getName() == com.codegym.model.RoleName.ROLE_STAFF
                            || r.getName() == com.codegym.model.RoleName.ROLE_DOCTOR);
            if (hasOtherStaffRole) {
                throw new IllegalArgumentException("Chỉ tài khoản bệnh nhân mới được liên kết lịch hẹn.");
            }
            com.codegym.model.Role patientRole = roleRepository.findByName(com.codegym.model.RoleName.ROLE_PATIENT)
                    .orElseGet(() -> roleRepository.save(new com.codegym.model.Role(com.codegym.model.RoleName.ROLE_PATIENT)));
            patientAccount.getRoles().add(patientRole);
            patientAccount = userRepository.save(patientAccount);
        }
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
        if (!findScheduledTimeSlotIds(doctor.getId(), dto.getAppointmentDate()).contains(timeSlot.getId())) {
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
        appointment.setPaymentMethod(com.codegym.model.PaymentMethod.VNPAY);
        appointment.setPaymentStatus(com.codegym.model.PaymentStatus.UNPAID);
        appointment.setPaymentAmount(doctor.getConsultationFee() != null ? doctor.getConsultationFee() : java.math.BigDecimal.valueOf(300000));
        if (patientAccount != null) {
            appointment.setPatientAccount(patientAccount);
        }

        return appointmentRepository.save(appointment);
    }

    private java.util.Set<Long> findScheduledTimeSlotIds(Long doctorId, LocalDate date) {
        java.util.List<DoctorWorkShift> dateShifts =
                doctorDateScheduleRepository.findShiftsByDoctorAndDate(doctorId, date);
        if (!dateShifts.isEmpty()) {
            if (dateShifts.contains(DoctorWorkShift.OFF)) {
                return java.util.Set.of();
            }
            return timeSlotRepository.findByActiveTrueOrderByStartTimeAsc().stream()
                    .filter(slot -> dateShifts.contains(slot.getStartTime().getHour() < 12
                            ? DoctorWorkShift.MORNING : DoctorWorkShift.AFTERNOON))
                    .map(TimeSlot::getId)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return new java.util.HashSet<>(
                doctorScheduleRepository.findTimeSlotIdsByDoctorAndDay(doctorId, date.getDayOfWeek()));
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
    @Transactional(readOnly = true)
    public Appointment findVerifiedAppointment(String bookingCode, String patientPhone) {
        if (bookingCode == null || patientPhone == null) {
            return null;
        }
        Appointment appointment = findByBookingCode(bookingCode.trim().toUpperCase());
        return appointment != null && java.util.Objects.equals(
                appointment.getPatientPhone(), patientPhone.trim()) ? appointment : null;
    }

    @Override
    @Transactional
    public void cancelAppointmentByBookingCode(String bookingCode, String patientPhone) {
        Appointment appointment = findVerifiedAppointment(bookingCode, patientPhone);
        if (appointment == null) {
            throw new IllegalArgumentException("Không tìm thấy phiếu khám với mã và số điện thoại đã nhập!");
        }
        if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException(
                    "Lịch hẹn đã thanh toán không thể hủy. Vui lòng liên hệ trực tiếp phòng khám nếu cần hỗ trợ.");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Lịch hẹn này đã bị hủy trước đó!");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Lịch hẹn đã hoàn thành, không thể hủy!");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
    }

    @Override
    public Page<Appointment> findAllAppointments(AppointmentStatus status, LocalDate date,
                                                 Long specialtyId, String doctorName, Pageable pageable) {
        String normalizedDoctorName = doctorName == null || doctorName.isBlank() ? null : doctorName.trim();
        return appointmentRepository.searchAppointments(status, date, specialtyId, normalizedDoctorName, pageable);
    }

    @Override
    public java.util.List<Appointment> findAppointmentsForCalendar(LocalDate startDate, LocalDate endDate,
                                                                    AppointmentStatus status, Long specialtyId) {
        return appointmentRepository.findCalendarAppointments(startDate, endDate, status, specialtyId);
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
                        "Không thể chuyển từ \"" + statusLabel(appointment.getStatus()) + "\" sang \""
                                + statusLabel(newStatus) + "\". " + allowedTransitionsMessage(appointment.getStatus()));
            }
            appointment.setStatus(newStatus);
            appointmentRepository.save(appointment);
            return true;
        }
        return false;
    }

    private String statusLabel(AppointmentStatus status) {
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case CANCELLED -> "Đã hủy";
            case COMPLETED -> "Đã hoàn thành";
        };
    }

    private String allowedTransitionsMessage(AppointmentStatus status) {
        return switch (status) {
            case PENDING -> "Lịch chờ xác nhận chỉ có thể chuyển sang đã xác nhận hoặc đã hủy.";
            case CONFIRMED -> "Lịch đã xác nhận chỉ có thể chuyển sang đã hoàn thành hoặc đã hủy.";
            case CANCELLED, COMPLETED -> "Lịch này không thể chuyển sang trạng thái khác.";
        };
    }

    @Override
    @Transactional
    public boolean updateClinicalResult(Long appointmentId, String clinicalResult) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return false;
        }
        setClinicalResult(appointment, clinicalResult);
        appointmentRepository.save(appointment);
        return true;
    }

    @Override
    @Transactional
    public boolean updateAppointmentDetails(Long appointmentId, AppointmentStatus newStatus,
                                            String clinicalResult, boolean updateClinicalResult) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return false;
        }
        if (newStatus != null) {
            if (!isAllowedTransition(appointment.getStatus(), newStatus)) {
                throw new IllegalArgumentException(
                        "Không thể chuyển từ \"" + statusLabel(appointment.getStatus()) + "\" sang \""
                                + statusLabel(newStatus) + "\". " + allowedTransitionsMessage(appointment.getStatus()));
            }
            appointment.setStatus(newStatus);
        }
        if (updateClinicalResult) {
            setClinicalResult(appointment, clinicalResult);
        }
        appointmentRepository.save(appointment);
        return true;
    }

    private void setClinicalResult(Appointment appointment, String clinicalResult) {
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Chỉ có thể lưu kết quả sau khi lịch hẹn hoàn thành.");
        }
        if (clinicalResult == null || clinicalResult.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập kết quả khám.");
        }
        if (clinicalResult.length() > 10000) {
            throw new IllegalArgumentException("Kết quả khám không được vượt quá 10.000 ký tự.");
        }
        appointment.setClinicalResult(clinicalResult.trim());
    }

    @Override
    @Transactional
    public boolean reschedulePatientAppointment(Long appointmentId, Long patientAccountId,
                                                AppointmentRescheduleForm form) {
        Appointment appointment = appointmentRepository.findByIdAndPatientAccountId(appointmentId, patientAccountId)
                .orElse(null);
        if (appointment == null) {
            return false;
        }
        if (!isPatientAppointmentChangeable(appointment)) {
            throw new IllegalArgumentException("Chỉ có thể chỉnh sửa lịch hẹn sắp diễn ra.");
        }
        if (form.getAppointmentDate() == null || !form.getAppointmentDate().isAfter(LocalDate.now())) {
            throw new InvalidAppointmentSelectionException("appointmentDate",
                    "Ngày khám phải là một ngày trong tương lai.");
        }
        Doctor doctor = doctorRepository.findByIdForUpdate(form.getDoctorId())
                .orElseThrow(() -> new InvalidAppointmentSelectionException(
                        "doctorId", "Không tìm thấy bác sĩ đã chọn."));
        if (!doctor.isActive() || doctor.getSpecialty() == null || !doctor.getSpecialty().isActive()) {
            throw new InvalidAppointmentSelectionException(
                    "doctorId", "Bác sĩ đã tạm ngừng nhận lịch. Vui lòng chọn bác sĩ khác.");
        }
        TimeSlot timeSlot = timeSlotRepository.findById(form.getTimeSlotId())
                .orElseThrow(() -> new InvalidAppointmentSelectionException(
                        "timeSlotId", "Không tìm thấy ca khám đã chọn."));
        if (!timeSlot.isActive()) {
            throw new InvalidAppointmentSelectionException(
                    "timeSlotId", "Khung giờ này không còn nhận đặt lịch.");
        }
        if (!findScheduledTimeSlotIds(doctor.getId(), form.getAppointmentDate()).contains(timeSlot.getId())) {
            throw new InvalidAppointmentSelectionException(
                    "timeSlotId", "Bác sĩ không làm việc vào ngày hoặc ca khám đã chọn.");
        }
        if (appointmentRepository.existsByDoctorIdAndAppointmentDateAndTimeSlotIdAndIdNotAndStatusNot(
                doctor.getId(), form.getAppointmentDate(), timeSlot.getId(), appointmentId,
                AppointmentStatus.CANCELLED)) {
            throw new AppointmentSlotUnavailableException();
        }

        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(form.getAppointmentDate());
        appointment.setTimeSlot(timeSlot);
        appointmentRepository.save(appointment);
        return true;
    }

    @Override
    @Transactional
    public boolean cancelPatientAppointment(Long appointmentId, Long patientAccountId) {
        Appointment appointment = appointmentRepository.findByIdAndPatientAccountId(appointmentId, patientAccountId)
                .orElse(null);
        if (appointment == null) {
            return false;
        }
        if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Lịch hẹn đã thanh toán không thể tự hủy trên hệ thống.");
        }
        if (!isPatientAppointmentChangeable(appointment)) {
            throw new IllegalArgumentException("Chỉ có thể hủy lịch hẹn sắp diễn ra.");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        return true;
    }

    boolean isPatientAppointmentChangeable(Appointment appointment) {
        return appointment.getAppointmentDate().isAfter(LocalDate.now())
                && appointment.getPaymentStatus() != PaymentStatus.PAID
                && (appointment.getStatus() == AppointmentStatus.PENDING
                || appointment.getStatus() == AppointmentStatus.CONFIRMED);
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