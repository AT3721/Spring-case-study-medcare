package com.codegym.controller.admin;

import com.codegym.dto.admin.DoctorAdminForm;
import com.codegym.dto.admin.SpecialtyAdminForm;
import com.codegym.dto.admin.CatalogScheduleForm;
import com.codegym.dto.admin.DoctorAdminForm;
import com.codegym.dto.admin.SpecialtyAdminForm;
import com.codegym.dto.admin.CatalogScheduleForm;
import com.codegym.dto.admin.DoctorAdminForm;
import com.codegym.dto.admin.SpecialtyAdminForm;
import com.codegym.dto.admin.CatalogScheduleForm;
import com.codegym.model.Doctor;
import com.codegym.model.DoctorSchedule;
import com.codegym.model.DoctorDateSchedule;
import com.codegym.model.DoctorWorkShift;
import com.codegym.model.Specialty;
import com.codegym.model.TimeSlot;
import com.codegym.model.User;
import com.codegym.repository.IDoctorRepository;
import com.codegym.repository.IDoctorScheduleRepository;
import com.codegym.repository.IDoctorDateScheduleRepository;
import com.codegym.repository.ISpecialtyRepository;
import com.codegym.repository.ITimeSlotRepository;
import com.codegym.repository.IUserRepository;
import com.codegym.service.doctor.AdminDoctorCatalogService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

@Controller
@RequestMapping("/admin/catalog")
public class AdminCatalogController {
    private final IDoctorRepository doctorRepository;
    private final ISpecialtyRepository specialtyRepository;
    private final IUserRepository userRepository;
    private final IDoctorScheduleRepository doctorScheduleRepository;
    private final IDoctorDateScheduleRepository doctorDateScheduleRepository;
    private final AdminDoctorCatalogService adminDoctorCatalogService;

    public AdminCatalogController(IDoctorRepository doctorRepository,
                                  ISpecialtyRepository specialtyRepository,
                                  IUserRepository userRepository,
                                  IDoctorScheduleRepository doctorScheduleRepository,
                                  IDoctorDateScheduleRepository doctorDateScheduleRepository,
                                  AdminDoctorCatalogService adminDoctorCatalogService) {
        this.doctorRepository = doctorRepository;
        this.specialtyRepository = specialtyRepository;
        this.userRepository = userRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.doctorDateScheduleRepository = doctorDateScheduleRepository;
        this.adminDoctorCatalogService = adminDoctorCatalogService;
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

    @GetMapping("/schedules")
    public String schedules(
            @ModelAttribute CatalogScheduleForm filters,
            Model model) {
        YearMonth month = filters.getMonth();
        Long specialtyId = filters.getSpecialtyId();
        List<Specialty> specialties = specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name"));
        Specialty selectedSpecialty = specialties.stream()
                .filter(specialty -> specialty.getId().equals(specialtyId))
                .findFirst()
                .orElse(specialties.isEmpty() ? null : specialties.get(0));
        List<Doctor> doctors = selectedSpecialty == null
                ? List.of()
                : doctorRepository.findAll(org.springframework.data.domain.Sort.by("name")).stream()
                        .filter(doctor -> doctor.getSpecialty().getId().equals(selectedSpecialty.getId()))
                        .toList();
        YearMonth selectedMonth = month == null ? YearMonth.now() : month;
        LocalDate monthStart = selectedMonth.atDay(1);
        LocalDate monthEnd = selectedMonth.atEndOfMonth();
        List<LocalDate> calendarDays = monthStart.datesUntil(monthEnd.plusDays(1)).toList();
        Map<LocalDate, Map<Long, Set<DoctorWorkShift>>> dateShiftsByDateAndDoctor =
                doctorDateScheduleRepository.findByScheduleDateBetweenOrderByScheduleDateAsc(monthStart, monthEnd)
                        .stream()
                        .collect(Collectors.groupingBy(
                                DoctorDateSchedule::getScheduleDate,
                                Collectors.groupingBy(
                                        schedule -> schedule.getDoctor().getId(),
                                        Collectors.mapping(DoctorDateSchedule::getShift, Collectors.toSet()))));
        Map<String, Map<String, List<String>>> doctorsByDateAndShift = new HashMap<>();
        calendarDays.forEach(day -> doctorsByDateAndShift.put(day.toString(), Map.of(
                "morning", new ArrayList<>(),
                "afternoon", new ArrayList<>())));

        for (Doctor doctor : doctors) {
            Map<DayOfWeek, Set<DoctorWorkShift>> weeklyShiftsByDay = new EnumMap<>(DayOfWeek.class);
            for (DoctorSchedule schedule : doctorScheduleRepository.findByDoctorId(doctor.getId())) {
                DoctorWorkShift shift = schedule.getTimeSlot().getStartTime().getHour() < 12
                        ? DoctorWorkShift.MORNING : DoctorWorkShift.AFTERNOON;
                weeklyShiftsByDay.computeIfAbsent(schedule.getDayOfWeek(), ignored -> new HashSet<>()).add(shift);
            }
            String displayName = doctor.getName().replaceFirst(
                    "^(?:(?:PGS|GS|TS|ThS|BS|CKI|CKII)\\.?\\s*)+", "");
            for (LocalDate day : calendarDays) {
                Set<DoctorWorkShift> assignedShifts = dateShiftsByDateAndDoctor
                        .getOrDefault(day, Map.of())
                        .get(doctor.getId());
                if (assignedShifts == null) {
                    assignedShifts = weeklyShiftsByDay.getOrDefault(day.getDayOfWeek(), Set.of());
                }
                if (!assignedShifts.contains(DoctorWorkShift.OFF)) {
                    Map<String, List<String>> dayAssignments = doctorsByDateAndShift.get(day.toString());
                    if (assignedShifts.contains(DoctorWorkShift.MORNING)) {
                        dayAssignments.get("morning").add(displayName);
                    }
                    if (assignedShifts.contains(DoctorWorkShift.AFTERNOON)) {
                        dayAssignments.get("afternoon").add(displayName);
                    }
                }
            }
        }
        model.addAttribute("doctors", doctors);
        model.addAttribute("specialties", specialties);
        model.addAttribute("selectedSpecialty", selectedSpecialty);
        model.addAttribute("selectedSpecialtyId", selectedSpecialty == null ? null : selectedSpecialty.getId());
        model.addAttribute("doctorsByDateAndShift", doctorsByDateAndShift);
        model.addAttribute("calendarDays", calendarDays);
        model.addAttribute("calendarMonth", selectedMonth);
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
        model.addAttribute("calendarMonthLabel", "Tháng " + selectedMonth.getMonthValue()
                + " năm " + selectedMonth.getYear());
        return "admin/catalog/schedules";
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
    public String saveDoctorSchedule(@PathVariable Long id,
                                     @ModelAttribute CatalogScheduleForm form,
                                     RedirectAttributes redirectAttributes) {
        List<String> shiftSelections = form.getShiftSelections();
        try {
            if (!adminDoctorCatalogService.saveWeeklySchedule(id, shiftSelections)) {
                redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy bác sĩ.");
                return "redirect:/admin/catalog/doctors";
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMsg", ex.getMessage());
            return "redirect:/admin/catalog/doctors/" + id + "/schedule";
        }
        redirectAttributes.addFlashAttribute("successMsg", "Đã lưu lịch làm việc của bác sĩ.");
        return "redirect:/admin/catalog/doctors/" + id + "/schedule";
    }

    private void populateScheduleModel(Doctor doctor, Model model) {
        Set<String> selectedShifts = doctorScheduleRepository.findByDoctorId(doctor.getId()).stream()
                .map(schedule -> schedule.getDayOfWeek().name() + "-"
                        + (schedule.getTimeSlot().getStartTime().getHour() < 12 ? "MORNING" : "AFTERNOON"))
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
        model.addAttribute("selectedShifts", selectedShifts);
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
        form.setImageUrl(doctor.getImageUrl());
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
    public String saveDoctor(@Valid @ModelAttribute("doctorForm") DoctorAdminForm form,
                             BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        AdminDoctorCatalogService.DoctorSaveResult saved = result.hasErrors() ? null
                : adminDoctorCatalogService.saveDoctor(form);
        if (saved != null) {
            saved.issues().forEach(issue -> {
                if (issue.field() == null) {
                    result.reject(issue.code(), issue.message());
                } else {
                    result.rejectValue(issue.field(), issue.code(), issue.message());
                }
            });
        }
        if (result.hasErrors()) {
            model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
            model.addAttribute("editing", form.getId() != null);
            model.addAttribute("hasDoctorAccount", saved != null && saved.hasDoctorAccount());
            return "admin/catalog/doctor-form";
        }

        redirectAttributes.addFlashAttribute("successMsg", "Đã lưu hồ sơ và tài khoản bác sĩ.");
        return "redirect:/admin/catalog/doctors";
    }

    @PostMapping("/doctors/{id}/toggle")
    public String toggleDoctor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String message = adminDoctorCatalogService.toggleDoctor(id);
            if (message == null) {
                redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy bác sĩ.");
            } else {
                redirectAttributes.addFlashAttribute("successMsg", message);
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMsg", ex.getMessage());
        }
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
        if (!result.hasErrors()) {
            adminDoctorCatalogService.saveSpecialty(form).forEach(issue -> {
                if (issue.field() == null) {
                    result.reject(issue.code(), issue.message());
                } else {
                    result.rejectValue(issue.field(), issue.code(), issue.message());
                }
            });
        }
        if (result.hasErrors()) {
            model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
            return "admin/catalog/specialties";
        }
        redirectAttributes.addFlashAttribute("successMsg", "Đã lưu chuyên khoa.");
        return "redirect:/admin/catalog/specialties";
    }

    @PostMapping("/specialties/{id}/toggle")
    public String toggleSpecialty(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String message = adminDoctorCatalogService.toggleSpecialty(id);
        if (message == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy chuyên khoa.");
        } else {
            redirectAttributes.addFlashAttribute("successMsg", message);
        }
        return "redirect:/admin/catalog/specialties";
    }
}
