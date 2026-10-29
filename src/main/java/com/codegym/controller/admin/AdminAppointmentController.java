package com.codegym.controller.admin;

import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.dto.appointment.AdminAppointmentForm;
import com.codegym.repository.ISpecialtyRepository;
import com.codegym.service.appointment.IAppointmentService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

@Controller
@RequestMapping("/admin/appointments")
public class AdminAppointmentController {

    @Autowired
    private IAppointmentService appointmentService;
    @Autowired
    private ISpecialtyRepository specialtyRepository;

    @GetMapping
    public String listAppointments(
            @ModelAttribute AdminAppointmentForm filters,
            Model model) {
        AppointmentStatus status = filters.getStatus();
        Long specialtyId = filters.getSpecialtyId();
        YearMonth month = filters.getMonth();
        YearMonth selectedMonth = month == null ? YearMonth.now() : month;
        LocalDate firstVisibleDay = selectedMonth.atDay(1)
                .with(DayOfWeek.MONDAY);
        LocalDate lastVisibleDay = selectedMonth.atEndOfMonth()
                .with(DayOfWeek.SUNDAY);
        List<LocalDate> calendarDays = firstVisibleDay.datesUntil(lastVisibleDay.plusDays(1)).toList();
        List<com.codegym.model.Appointment> calendarAppointments =
                appointmentService.findAppointmentsForCalendar(firstVisibleDay, lastVisibleDay, status, specialtyId);
        Map<String, List<com.codegym.model.Appointment>> appointmentsByDate = calendarAppointments.stream()
                .collect(Collectors.groupingBy(appointment -> appointment.getAppointmentDate().toString()));

        model.addAttribute("appointmentCount", calendarAppointments.size());
        model.addAttribute("calendarDays", calendarDays);
        model.addAttribute("appointmentsByDate", appointmentsByDate);
        model.addAttribute("calendarMonth", selectedMonth);
        model.addAttribute("calendarMonthLabel",
                selectedMonth.atDay(1).format(DateTimeFormatter.ofPattern("'tháng' M yyyy", Locale.forLanguageTag("vi-VN"))));
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
        model.addAttribute("specialties", specialtyRepository.findAll(org.springframework.data.domain.Sort.by("name")));
        model.addAttribute("selectedSpecialtyId", specialtyId);
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("selectedStatus", status);

        return "admin/appointment-list";
    }

    @PostMapping("/update-status")
    public String updateStatus(@ModelAttribute AdminAppointmentForm form,
                               RedirectAttributes redirectAttributes) {
        Long id = form.getId();
        AppointmentStatus status = form.getStatus();
        YearMonth month = form.getMonth();
        AppointmentStatus statusFilter = form.getStatusFilter();
        Long specialtyId = form.getSpecialtyId();
        try {
            boolean updated = appointmentService.updateStatus(id, status);
            if (updated) {
                redirectAttributes.addFlashAttribute("successMsg", "Đã cập nhật trạng thái phiếu khám thành công!");
            } else {
                redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy phiếu khám!");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMsg", ex.getMessage());
            redirectAttributes.addFlashAttribute("statusErrorMsg", ex.getMessage());
            return appointmentListRedirect(month, statusFilter, specialtyId, id);
        }
        return appointmentListRedirect(month, statusFilter, specialtyId);
    }

    @PostMapping("/update-result")
    public String updateClinicalResult(@ModelAttribute AdminAppointmentForm form,
                                       RedirectAttributes redirectAttributes) {
        Long id = form.getId();
        String clinicalResult = form.getClinicalResult();
        YearMonth month = form.getMonth();
        AppointmentStatus statusFilter = form.getStatusFilter();
        Long specialtyId = form.getSpecialtyId();
        try {
            boolean updated = appointmentService.updateClinicalResult(id, clinicalResult);
            if (updated) {
                redirectAttributes.addFlashAttribute("successMsg", "Đã lưu kết quả khám.");
            } else {
                redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy lịch hẹn.");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return appointmentListRedirect(month, statusFilter, specialtyId);
    }

    @PostMapping("/update-details")
    public String updateAppointmentDetails(@ModelAttribute AdminAppointmentForm form,
                                           Authentication authentication,
                                           RedirectAttributes redirectAttributes) {
        Long id = form.getId();
        AppointmentStatus status = form.getStatus();
        String clinicalResult = form.getClinicalResult();
        boolean clinicalResultChanged = form.isClinicalResultChanged();
        YearMonth month = form.getMonth();
        AppointmentStatus statusFilter = form.getStatusFilter();
        Long specialtyId = form.getSpecialtyId();
        if (status != null && authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Chỉ quản trị viên mới có thể cập nhật trạng thái lịch hẹn.");
        }
        try {
            boolean updated = appointmentService.updateAppointmentDetails(
                    id, status, clinicalResult, clinicalResultChanged);
            if (updated) {
                redirectAttributes.addFlashAttribute("successMsg", "Đã lưu thông tin lịch hẹn.");
            } else {
                redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy lịch hẹn.");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMsg", ex.getMessage());
            redirectAttributes.addFlashAttribute("modalErrorMsg", ex.getMessage());
            return appointmentListRedirect(month, statusFilter, specialtyId, id);
        }
        return appointmentListRedirect(month, statusFilter, specialtyId);
    }

    private String appointmentListRedirect(YearMonth month, AppointmentStatus status, Long specialtyId) {
        return appointmentListRedirect(month, status, specialtyId, null);
    }

    private String appointmentListRedirect(YearMonth month, AppointmentStatus status, Long specialtyId,
                                           Long appointmentId) {
        StringBuilder redirect = new StringBuilder("redirect:/admin/appointments");
        boolean hasQuery = false;
        if (month != null) {
            redirect.append("?month=").append(month);
            hasQuery = true;
        }
        if (status != null) {
            redirect.append(hasQuery ? "&" : "?").append("status=").append(status);
            hasQuery = true;
        }
        if (specialtyId != null) {
            redirect.append(hasQuery ? "&" : "?").append("specialtyId=").append(specialtyId);
            hasQuery = true;
        }
        if (appointmentId != null) {
            redirect.append(hasQuery ? "&" : "?").append("appointmentId=").append(appointmentId);
        }
        return redirect.toString();
    }
}