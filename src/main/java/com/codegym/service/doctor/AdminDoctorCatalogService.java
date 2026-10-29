package com.codegym.service.doctor;

import com.codegym.dto.admin.DoctorAdminForm;
import com.codegym.dto.admin.SpecialtyAdminForm;
import com.codegym.model.Doctor;
import com.codegym.model.DoctorSchedule;
import com.codegym.model.Role;
import com.codegym.model.RoleName;
import com.codegym.model.Specialty;
import com.codegym.model.TimeSlot;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.IDoctorScheduleRepository;
import com.codegym.repository.IRoleRepository;
import com.codegym.repository.ISpecialtyRepository;
import com.codegym.repository.ITimeSlotRepository;
import com.codegym.repository.IUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminDoctorCatalogService {
    private final IDoctorRepository doctorRepository;
    private final ISpecialtyRepository specialtyRepository;
    private final IUserRepository userRepository;
    private final IDoctorScheduleRepository doctorScheduleRepository;
    private final ITimeSlotRepository timeSlotRepository;
    private final IRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminDoctorCatalogService(IDoctorRepository doctorRepository, ISpecialtyRepository specialtyRepository,
                                     IUserRepository userRepository, IDoctorScheduleRepository doctorScheduleRepository,
                                     ITimeSlotRepository timeSlotRepository, IRoleRepository roleRepository,
                                     PasswordEncoder passwordEncoder) {
        this.doctorRepository = doctorRepository;
        this.specialtyRepository = specialtyRepository;
        this.userRepository = userRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DoctorSaveResult saveDoctor(DoctorAdminForm form) {
        List<ValidationIssue> issues = new ArrayList<>();
        Doctor doctor = form.getId() == null ? new Doctor()
                : doctorRepository.findById(form.getId()).orElse(null);
        if (form.getId() != null && doctor == null) {
            issues.add(new ValidationIssue(null, "doctor.missing", "Không tìm thấy bác sĩ cần cập nhật."));
        }
        Specialty specialty = form.getSpecialtyId() == null ? null
                : specialtyRepository.findById(form.getSpecialtyId()).orElse(null);
        if (specialty == null) {
            issues.add(new ValidationIssue("specialtyId", "specialty.invalid", "Vui lòng chọn chuyên khoa hợp lệ."));
        } else if (form.isActive() && !specialty.isActive()) {
            issues.add(new ValidationIssue("specialtyId", "specialty.inactive",
                    "Không thể kích hoạt bác sĩ thuộc chuyên khoa đang ẩn."));
        }
        String imageUrl = form.getImageUrl();
        if (imageUrl != null && !imageUrl.isBlank()
                && (!imageUrl.startsWith("/images/doctors/") || imageUrl.contains("..")
                || imageUrl.contains("\\") || imageUrl.contains(":"))) {
            issues.add(new ValidationIssue("imageUrl", "image.invalid",
                    "Ảnh phải là đường dẫn trong thư mục /images/doctors/."));
        }
        var linkedUser = form.getId() == null ? null : userRepository.findByDoctorId(form.getId()).orElse(null);
        if (linkedUser == null && (form.getPassword() == null || form.getPassword().length() < 12)) {
            issues.add(new ValidationIssue("password", "password.required",
                    "Mật khẩu tài khoản bác sĩ phải có ít nhất 12 ký tự."));
        } else if (form.getPassword() != null && !form.getPassword().isBlank()
                && form.getPassword().length() < 12) {
            issues.add(new ValidationIssue("password", "password.short", "Mật khẩu phải có ít nhất 12 ký tự."));
        }
        var usernameOwner = form.getUsername() == null ? null
                : userRepository.findByUsername(form.getUsername().trim()).orElse(null);
        if (usernameOwner != null && usernameOwner != linkedUser) {
            issues.add(new ValidationIssue("username", "username.taken", "Tên đăng nhập đã được sử dụng."));
        }
        if (!issues.isEmpty()) {
            return new DoctorSaveResult(issues, linkedUser != null);
        }

        doctor.setName(form.getName().trim());
        doctor.setDegree(form.getDegree());
        doctor.setConsultationFee(form.getConsultationFee());
        doctor.setBio(form.getBio());
        doctor.setImageUrl(imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim());
        doctor.setSpecialty(specialty);
        doctor.setActive(form.isActive());
        Doctor savedDoctor = doctorRepository.save(doctor);

        Role doctorRole = roleRepository.findByName(RoleName.ROLE_DOCTOR)
                .orElseThrow(() -> new IllegalStateException("Thiếu role ROLE_DOCTOR."));
        var doctorUser = linkedUser == null ? new com.codegym.model.User() : linkedUser;
        doctorUser.setUsername(form.getUsername().trim());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            doctorUser.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        doctorUser.setFullName(savedDoctor.getName());
        doctorUser.setEnabled(form.isActive());
        doctorUser.setDoctor(savedDoctor);
        doctorUser.setRoles(new HashSet<>(List.of(doctorRole)));
        userRepository.save(doctorUser);
        return new DoctorSaveResult(List.of(), true);
    }

    @Transactional
    public List<ValidationIssue> saveSpecialty(SpecialtyAdminForm form) {
        Specialty specialty = form.getId() == null ? new Specialty()
                : specialtyRepository.findById(form.getId()).orElse(null);
        if (form.getId() != null && specialty == null) {
            return List.of(new ValidationIssue(null, "specialty.missing",
                    "Không tìm thấy chuyên khoa cần cập nhật."));
        }
        specialty.setName(form.getName().trim());
        specialty.setDescription(form.getDescription());
        specialty.setIconUrl(form.getIconUrl());
        specialty.setActive(form.isActive());
        specialtyRepository.save(specialty);
        return List.of();
    }

    @Transactional
    public boolean saveWeeklySchedule(Long doctorId, List<String> shiftSelections) {
        Doctor doctor = doctorRepository.findByIdForUpdate(doctorId).orElse(null);
        if (doctor == null) {
            return false;
        }
        Map<DayOfWeek, Set<String>> selectionsByDay = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            selectionsByDay.put(day, new HashSet<>());
        }
        if (shiftSelections != null) {
            for (String selection : shiftSelections) {
                String[] parts = selection.split("-", -1);
                if (parts.length != 2 || !parts[1].equals("MORNING") && !parts[1].equals("AFTERNOON")) {
                    throw new IllegalArgumentException("Lựa chọn ca khám không hợp lệ. Lịch chưa được thay đổi.");
                }
                try {
                    selectionsByDay.get(DayOfWeek.valueOf(parts[0])).add(parts[1]);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(
                            "Lựa chọn ca khám không hợp lệ. Lịch chưa được thay đổi.", ex);
                }
            }
        }

        List<TimeSlot> activeSlots = timeSlotRepository.findByActiveTrueOrderByStartTimeAsc();
        doctorScheduleRepository.deleteByDoctorId(doctorId);
        List<DoctorSchedule> schedules = new ArrayList<>();
        selectionsByDay.forEach((day, shifts) -> shifts.forEach(shift ->
                activeSlots.stream()
                        .filter(slot -> shift.equals("MORNING")
                                ? slot.getStartTime().getHour() < 12 : slot.getStartTime().getHour() >= 12)
                        .forEach(slot -> schedules.add(new DoctorSchedule(doctor, day, slot)))));
        doctorScheduleRepository.saveAll(schedules);
        return true;
    }

    @Transactional
    public String toggleDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id).orElse(null);
        if (doctor == null) {
            return null;
        }
        if (!doctor.isActive() && !doctor.getSpecialty().isActive()) {
            throw new IllegalArgumentException("Hãy kích hoạt chuyên khoa trước khi mở lại bác sĩ.");
        }
        doctor.setActive(!doctor.isActive());
        userRepository.findByDoctorId(id).ifPresent(user -> user.setEnabled(doctor.isActive()));
        return doctor.isActive() ? "Đã mở hồ sơ bác sĩ." : "Đã ẩn hồ sơ bác sĩ.";
    }

    @Transactional
    public String toggleSpecialty(Long id) {
        Specialty specialty = specialtyRepository.findById(id).orElse(null);
        if (specialty == null) {
            return null;
        }
        specialty.setActive(!specialty.isActive());
        if (!specialty.isActive()) {
            doctorRepository.findAll().stream()
                    .filter(doctor -> doctor.getSpecialty().getId().equals(id))
                    .forEach(doctor -> {
                        doctor.setActive(false);
                        userRepository.findByDoctorId(doctor.getId()).ifPresent(user -> user.setEnabled(false));
                    });
        }
        specialtyRepository.save(specialty);
        return specialty.isActive()
                ? "Đã mở lại chuyên khoa. Bác sĩ vẫn cần được kích hoạt riêng."
                : "Đã ẩn chuyên khoa và các bác sĩ thuộc chuyên khoa này.";
    }

    public record ValidationIssue(String field, String code, String message) { }
    public record DoctorSaveResult(List<ValidationIssue> issues, boolean hasDoctorAccount) { }
}
