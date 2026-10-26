package com.codegym.controller;

import com.codegym.dto.DoctorAdminForm;
import com.codegym.dto.SpecialtyAdminForm;
import com.codegym.model.Doctor;
import com.codegym.model.DoctorSchedule;
import com.codegym.model.Role;
import com.codegym.model.RoleName;
import com.codegym.model.Specialty;
import com.codegym.model.TimeSlot;
import com.codegym.model.User;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.IDoctorScheduleRepository;
import com.codegym.repository.IRoleRepository;
import com.codegym.repository.ISpecialtyRepository;
import com.codegym.repository.ITimeSlotRepository;
import com.codegym.repository.IUserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Set;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

@Controller
@RequestMapping("/admin/catalog")
public class AdminCatalogController {
    private final IDoctorRepository doctorRepository;
    private final ISpecialtyRepository specialtyRepository;
    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final IDoctorScheduleRepository doctorScheduleRepository;
    private final ITimeSlotRepository timeSlotRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminCatalogController(IDoctorRepository doctorRepository,
                                  ISpecialtyRepository specialtyRepository,
                                  IUserRepository userRepository,
                                  IRoleRepository roleRepository,
                                  IDoctorScheduleRepository doctorScheduleRepository,
                                  ITimeSlotRepository timeSlotRepository,
                                  PasswordEncoder passwordEncoder) {
        this.doctorRepository = doctorRepository;
        this.specialtyRepository = specialtyRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/doctors")
    public String doctors(Model model) {
        List<Doctor> doctors = doctorRepository.findAll(org.springframework.data.domain.Sort.by("name"));
        Map<Long, String> usernames = doctors.stream()
                .map(doctor -> Map.entry(doctor.getId(),
                        userRepository.findByDoctorId(doctor.getId()).map(User::getUsername).orElse("—")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        model.addAttribute("doctors", doctors);
        model.addAttribute("doctorUsernames", usernames);
        return "admin/catalog/doctors";
    }

    @GetMapping("/doctors/{id}/schedule")
    public String doctorSchedule(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Doctor doctor = doctorRepository.findById(id).orElse(null);
        if (doctor == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy bác sĩ.");
            return "redirect:/admin/catalog/doctors";
        }
        populateScheduleModel(doctor, model);
        return "admin/catalog/doctor-schedule";
    }

    @PostMapping("/doctors/{id}/schedule")
    @Transactional
    public String saveDoctorSchedule(@PathVariable Long id,
                                     @RequestParam(name = "slotSelections", required = false) List<String> slotSelections,
                                     RedirectAttributes redirectAttributes) {
        Doctor doctor = doctorRepository.findByIdForUpdate(id).orElse(null);
        if (doctor == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy bác sĩ.");
            return "redirect:/admin/catalog/doctors";
        }

        List<TimeSlot> activeSlots = timeSlotRepository.findByActiveTrueOrderByStartTimeAsc();
        Map<Long, TimeSlot> slotsById = activeSlots.stream()
                .collect(Collectors.toMap(TimeSlot::getId, slot -> slot));
        Map<DayOfWeek, Set<Long>> selectionsByDay = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            selectionsByDay.put(day, new HashSet<>());
        }
        try {
            if (slotSelections != null) {
                for (String selection : slotSelections) {
                    String[] parts = selection.split("-", -1);
                    if (parts.length != 2) {
                        throw new IllegalArgumentException();
                    }
                    DayOfWeek day = DayOfWeek.valueOf(parts[0]);
                    Long slotId = Long.valueOf(parts[1]);
                    if (!slotsById.containsKey(slotId)) {
                        throw new IllegalArgumentException();
                    }
                    selectionsByDay.get(day).add(slotId);
                }
            }
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMsg", "Lựa chọn ca khám không hợp lệ. Lịch chưa được thay đổi.");
            return "redirect:/admin/catalog/doctors/" + id + "/schedule";
        }

        doctorScheduleRepository.deleteByDoctorId(id);
        List<DoctorSchedule> schedules = new ArrayList<>();
        selectionsByDay.forEach((day, slotIds) -> slotIds.forEach(slotId ->
                schedules.add(new DoctorSchedule(doctor, day, slotsById.get(slotId)))));
        doctorScheduleRepository.saveAll(schedules);
        redirectAttributes.addFlashAttribute("successMsg", "Đã lưu lịch làm việc của bác sĩ.");
        return "redirect:/admin/catalog/doctors/" + id + "/schedule";
    }

    private void populateScheduleModel(Doctor doctor, Model model) {
        Set<String> selectedSlots = doctorScheduleRepository.findByDoctorId(doctor.getId()).stream()
                .map(schedule -> schedule.getDayOfWeek().name() + "-" + schedule.getTimeSlot().getId())
                .collect(Collectors.toSet());
        Map<String, String> dayLabels = Map.of(
                DayOfWeek.MONDAY.name(), "Thứ 2",
                DayOfWeek.TUESDAY.name(), "Thứ 3",
                DayOfWeek.WEDNESDAY.name(), "Thứ 4",
                DayOfWeek.THURSDAY.name(), "Thứ 5",
                DayOfWeek.FRIDAY.name(), "Thứ 6",
                DayOfWeek.SATURDAY.name(), "Thứ 7",
                DayOfWeek.SUNDAY.name(), "Chủ Nhật");
        model.addAttribute("doctor", doctor);
        model.addAttribute("days", List.of(DayOfWeek.values()));
        model.addAttribute("dayLabels", dayLabels);
        model.addAttribute("timeSlots", timeSlotRepository.findByActiveTrueOrderByStartTimeAsc());
        model.addAttribute("selectedSlots", selectedSlots);
    }

    @GetMapping("/doctors/new")
    public String newDoctor(Model model) {
        model.addAttribute("doctorForm", new DoctorAdminForm());
        model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
        model.addAttribute("editing", false);
        model.addAttribute("hasDoctorAccount", false);
        return "admin/catalog/doctor-form";
    }

    @GetMapping("/doctors/{id}/edit")
    public String editDoctor(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Doctor doctor = doctorRepository.findById(id).orElse(null);
        if (doctor == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy bác sĩ.");
            return "redirect:/admin/catalog/doctors";
        }
        DoctorAdminForm form = new DoctorAdminForm();
        form.setId(doctor.getId());
        form.setName(doctor.getName());
        form.setDegree(doctor.getDegree());
        form.setConsultationFee(doctor.getConsultationFee());
        form.setBio(doctor.getBio());
        form.setSpecialtyId(doctor.getSpecialty().getId());
        form.setActive(doctor.isActive());
        User doctorUser = userRepository.findByDoctorId(id).orElse(null);
        boolean hasDoctorAccount = doctorUser != null;
        if (doctorUser != null) {
            form.setUsername(doctorUser.getUsername());
        }
        model.addAttribute("doctorForm", form);
        model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
        model.addAttribute("editing", true);
        model.addAttribute("hasDoctorAccount", hasDoctorAccount);
        return "admin/catalog/doctor-form";
    }

    @PostMapping("/doctors/save")
    @Transactional
    public String saveDoctor(@Valid @ModelAttribute("doctorForm") DoctorAdminForm form,
                             BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        Doctor doctor = form.getId() == null ? new Doctor()
                : doctorRepository.findById(form.getId()).orElse(null);
        if (form.getId() != null && doctor == null) {
            result.reject("doctor.missing", "Không tìm thấy bác sĩ cần cập nhật.");
        }
        Specialty specialty = form.getSpecialtyId() == null ? null
                : specialtyRepository.findById(form.getSpecialtyId()).orElse(null);
        if (specialty == null) {
            result.rejectValue("specialtyId", "specialty.invalid", "Vui lòng chọn chuyên khoa hợp lệ.");
        } else if (form.isActive() && !specialty.isActive()) {
            result.rejectValue("specialtyId", "specialty.inactive", "Không thể kích hoạt bác sĩ thuộc chuyên khoa đang ẩn.");
        }
        User linkedUser = form.getId() == null ? null : userRepository.findByDoctorId(form.getId()).orElse(null);
        if (linkedUser == null && (form.getPassword() == null || form.getPassword().length() < 12)) {
            result.rejectValue("password", "password.required", "Mật khẩu tài khoản bác sĩ phải có ít nhất 12 ký tự.");
        } else if (form.getPassword() != null && !form.getPassword().isBlank() && form.getPassword().length() < 12) {
            result.rejectValue("password", "password.short", "Mật khẩu phải có ít nhất 12 ký tự.");
        }
        User usernameOwner = form.getUsername() == null ? null
                : userRepository.findByUsername(form.getUsername().trim()).orElse(null);
        if (usernameOwner != null && usernameOwner != linkedUser) {
            result.rejectValue("username", "username.taken", "Tên đăng nhập đã được sử dụng.");
        }
        if (result.hasErrors()) {
            model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
            model.addAttribute("editing", form.getId() != null);
            model.addAttribute("hasDoctorAccount", linkedUser != null);
            return "admin/catalog/doctor-form";
        }

        doctor.setName(form.getName().trim());
        doctor.setDegree(form.getDegree());
        doctor.setConsultationFee(form.getConsultationFee());
        doctor.setBio(form.getBio());
        doctor.setSpecialty(specialty);
        doctor.setActive(form.isActive());
        Doctor savedDoctor = doctorRepository.save(doctor);

        Role staffRole = roleRepository.findByName(RoleName.ROLE_STAFF)
                .orElseThrow(() -> new IllegalStateException("Thiếu role ROLE_STAFF."));
        User doctorUser = linkedUser == null ? new User() : linkedUser;
        doctorUser.setUsername(form.getUsername().trim());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            doctorUser.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        doctorUser.setFullName(savedDoctor.getName());
        doctorUser.setEnabled(form.isActive());
        doctorUser.setDoctor(savedDoctor);
        doctorUser.setRoles(new HashSet<>(List.of(staffRole)));
        userRepository.save(doctorUser);

        redirectAttributes.addFlashAttribute("successMsg", "Đã lưu hồ sơ và tài khoản bác sĩ.");
        return "redirect:/admin/catalog/doctors";
    }

    @PostMapping("/doctors/{id}/toggle")
    @Transactional
    public String toggleDoctor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Doctor doctor = doctorRepository.findById(id).orElse(null);
        if (doctor == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy bác sĩ.");
            return "redirect:/admin/catalog/doctors";
        }
        if (!doctor.isActive() && !doctor.getSpecialty().isActive()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Hãy kích hoạt chuyên khoa trước khi mở lại bác sĩ.");
            return "redirect:/admin/catalog/doctors";
        }
        doctor.setActive(!doctor.isActive());
        userRepository.findByDoctorId(id).ifPresent(user -> user.setEnabled(doctor.isActive()));
        redirectAttributes.addFlashAttribute("successMsg", doctor.isActive() ? "Đã mở hồ sơ bác sĩ." : "Đã ẩn hồ sơ bác sĩ.");
        return "redirect:/admin/catalog/doctors";
    }

    @GetMapping("/specialties")
    public String specialties(Model model) {
        model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
        model.addAttribute("specialtyForm", new SpecialtyAdminForm());
        return "admin/catalog/specialties";
    }

    @PostMapping("/specialties/save")
    public String saveSpecialty(@Valid @ModelAttribute("specialtyForm") SpecialtyAdminForm form,
                                BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
            return "admin/catalog/specialties";
        }
        Specialty specialty = form.getId() == null ? new Specialty()
                : specialtyRepository.findById(form.getId()).orElse(null);
        if (form.getId() != null && specialty == null) {
            result.reject("specialty.missing", "Không tìm thấy chuyên khoa cần cập nhật.");
        }
        if (specialty == null && form.getId() == null) {
            specialty = new Specialty();
        }
        if (result.hasErrors()) {
            model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
            return "admin/catalog/specialties";
        }
        specialty.setName(form.getName().trim());
        specialty.setDescription(form.getDescription());
        specialty.setIconUrl(form.getIconUrl());
        specialty.setActive(form.isActive());
        specialtyRepository.save(specialty);
        redirectAttributes.addFlashAttribute("successMsg", "Đã lưu chuyên khoa.");
        return "redirect:/admin/catalog/specialties";
    }

    @PostMapping("/specialties/{id}/toggle")
    @Transactional
    public String toggleSpecialty(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Specialty specialty = specialtyRepository.findById(id).orElse(null);
        if (specialty == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy chuyên khoa.");
            return "redirect:/admin/catalog/specialties";
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
        redirectAttributes.addFlashAttribute("successMsg", specialty.isActive()
                ? "Đã mở lại chuyên khoa. Bác sĩ vẫn cần được kích hoạt riêng."
                : "Đã ẩn chuyên khoa và các bác sĩ thuộc chuyên khoa này.");
        return "redirect:/admin/catalog/specialties";
    }
}
