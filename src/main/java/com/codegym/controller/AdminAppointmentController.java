package com.codegym.controller;

import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.service.IAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/appointments")
public class AdminAppointmentController {

    @Autowired
    private IAppointmentService appointmentService;

    @GetMapping
    public String listAppointments(
            @RequestParam(name = "status", required = false) AppointmentStatus status,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "doctorName", required = false) String doctorName,
            @RequestParam(name = "sortBy", defaultValue = "createdDesc") String sortBy,
            @PageableDefault(size = 8, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {

        Sort sort = switch (sortBy) {
            case "doctorAsc" -> Sort.by(Sort.Direction.ASC, "doctor.name").and(Sort.by("appointmentDate"));
            case "doctorDesc" -> Sort.by(Sort.Direction.DESC, "doctor.name").and(Sort.by("appointmentDate"));
            case "createdAsc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        Pageable sortedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        String normalizedDoctorName = doctorName == null ? "" : doctorName.trim();
        if (normalizedDoctorName.length() > 80) {
            normalizedDoctorName = normalizedDoctorName.substring(0, 80);
        }
        Page<Appointment> appointments = appointmentService.findAllAppointments(
                status, date, normalizedDoctorName, sortedPage);

        model.addAttribute("appointments", appointments);
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedDate", date);
        model.addAttribute("doctorName", normalizedDoctorName);
        model.addAttribute("selectedSortBy", sortBy);

        return "admin/appointment-list";
    }

    @PostMapping("/update-status")
    public String updateStatus(@RequestParam("id") Long id,
                               @RequestParam("status") AppointmentStatus status,
                               RedirectAttributes redirectAttributes) {
        try {
            boolean updated = appointmentService.updateStatus(id, status);
            if (updated) {
                redirectAttributes.addFlashAttribute("successMsg", "Đã cập nhật trạng thái phiếu khám thành công!");
            } else {
                redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy phiếu khám!");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMsg", ex.getMessage());
        }
        return "redirect:/admin/appointments";
    }
}