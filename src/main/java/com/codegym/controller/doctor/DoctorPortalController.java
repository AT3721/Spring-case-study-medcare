package com.codegym.controller.doctor;

import com.codegym.dto.doctor.PrescriptionForm;
import com.codegym.dto.doctor.DoctorPortalFilterForm;
import com.codegym.dto.doctor.DoctorDirectoryForm;
import com.codegym.dto.doctor.PrescriptionForm;
import com.codegym.dto.doctor.DoctorPortalFilterForm;
import com.codegym.dto.doctor.DoctorDirectoryForm;
import com.codegym.model.*;
import com.codegym.service.doctor.DoctorPortalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/doctor")
@PreAuthorize("hasAuthority('ROLE_DOCTOR')")
public class DoctorPortalController {

    private final DoctorPortalService doctorPortalService;

    public DoctorPortalController(DoctorPortalService doctorPortalService) {
        this.doctorPortalService = doctorPortalService;
    }

    @GetMapping({"", "/", "/appointments"})
    public String showDoctorAppointments(@ModelAttribute DoctorPortalFilterForm filters,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         Model model) {
        AppointmentStatus status = filters.getStatus();
        LocalDate date = filters.getDate();
        if (userDetails == null) {
            throw new IllegalStateException("Người dùng chưa đăng nhập.");
        }
        Doctor doctor = doctorPortalService.findDoctor(userDetails.getUsername());
        if (doctor == null) {
            model.addAttribute("errorMsg", "Tài khoản của bạn chưa được liên kết với hồ sơ bác sĩ.");
            return "doctor-portal/error";
        }

        var appointments = doctorPortalService.findAppointments(userDetails.getUsername(), status, date);

        model.addAttribute("doctor", doctor);
        model.addAttribute("appointments", appointments);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedDate", date);
        model.addAttribute("today", LocalDate.now());
        return "doctor-portal/appointments";
    }

    @GetMapping("/appointments/{id}")
    public String showAppointmentDetail(@PathVariable Long id,
                                        @AuthenticationPrincipal UserDetails userDetails,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            throw new IllegalStateException("Người dùng chưa đăng nhập.");
        }
        Doctor doctor = doctorPortalService.findDoctor(userDetails.getUsername());
        if (doctor == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Tài khoản chưa được liên kết với bác sĩ.");
            return "redirect:/doctor/appointments";
        }

        Appointment appointment = doctorPortalService.findAppointment(id, userDetails.getUsername());
        if (appointment == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy lịch khám hoặc bạn không phụ trách lịch khám này.");
            return "redirect:/doctor/appointments";
        }

        var prescriptions = doctorPortalService.findPrescriptions(id);

        model.addAttribute("doctor", doctor);
        model.addAttribute("appointment", appointment);
        model.addAttribute("prescriptions", prescriptions);
        if (!model.containsAttribute("prescriptionForm")) {
            model.addAttribute("prescriptionForm", new PrescriptionForm(id));
        }
        return "doctor-portal/appointment-detail";
    }

    @PostMapping("/appointments/{id}/clinical-result")
    public String updateClinicalResult(@PathVariable Long id,
                                       @ModelAttribute DoctorPortalFilterForm form,
                                       @AuthenticationPrincipal UserDetails userDetails,
                                       RedirectAttributes redirectAttributes) {
        String clinicalResult = form.getClinicalResult();
        boolean markCompleted = form.isMarkCompleted();
        if (userDetails == null || !doctorPortalService.updateClinicalResult(
                id, userDetails.getUsername(), clinicalResult, markCompleted)) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không có quyền thao tác trên lịch hẹn này.");
            return "redirect:/doctor/appointments";
        }

        redirectAttributes.addFlashAttribute("successMsg", "Đã cập nhật kết luận khám bệnh.");
        return "redirect:/doctor/appointments/" + id;
    }

    @PostMapping("/appointments/{appointmentId}/prescriptions/save")
    public String savePrescription(@PathVariable Long appointmentId,
                                   @Valid @ModelAttribute("prescriptionForm") PrescriptionForm form,
                                   BindingResult bindingResult,
                                   @AuthenticationPrincipal UserDetails userDetails,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (userDetails == null || doctorPortalService.findAppointment(appointmentId, userDetails.getUsername()) == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không tìm thấy lịch khám.");
            return "redirect:/doctor/appointments";
        }

        if (bindingResult.hasErrors()) {
            var doctor = doctorPortalService.findDoctor(userDetails.getUsername());
            var appointment = doctorPortalService.findAppointment(appointmentId, userDetails.getUsername());
            var prescriptions = doctorPortalService.findPrescriptions(appointmentId);
            model.addAttribute("doctor", doctor);
            model.addAttribute("appointment", appointment);
            model.addAttribute("prescriptions", prescriptions);
            return "doctor-portal/appointment-detail";
        }

        doctorPortalService.savePrescription(appointmentId, userDetails.getUsername(), form);
        redirectAttributes.addFlashAttribute("successMsg", "Đã lưu đơn thuốc thành công.");
        return "redirect:/doctor/appointments/" + appointmentId;
    }

    @PostMapping("/appointments/{appointmentId}/prescriptions/{prescriptionId}/delete")
    public String deletePrescription(@PathVariable Long appointmentId,
                                     @PathVariable Long prescriptionId,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        if (userDetails == null || doctorPortalService.findAppointment(appointmentId, userDetails.getUsername()) == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Không có quyền thao tác.");
            return "redirect:/doctor/appointments";
        }

        doctorPortalService.deletePrescription(appointmentId, prescriptionId, userDetails.getUsername());
        redirectAttributes.addFlashAttribute("successMsg", "Đã xóa mục thuốc.");
        return "redirect:/doctor/appointments/" + appointmentId;
    }
}
