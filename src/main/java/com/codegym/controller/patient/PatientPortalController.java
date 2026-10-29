package com.codegym.controller.patient;

import com.codegym.dto.appointment.AppointmentRescheduleForm;
import com.codegym.model.Appointment;
import com.codegym.model.User;
import com.codegym.service.appointment.PatientAppointmentService;
import com.codegym.service.appointment.IAppointmentService;
import com.codegym.service.appointment.AppointmentSlotUnavailableException;
import com.codegym.service.doctor.IDoctorService;
import com.codegym.service.auth.AuthenticatedUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
public class PatientPortalController {
    private final PatientAppointmentService patientAppointmentService;
    private final IAppointmentService appointmentService;
    private final IDoctorService doctorService;
    private final AuthenticatedUserService authenticatedUserService;

    public PatientPortalController(PatientAppointmentService patientAppointmentService,
                                   IAppointmentService appointmentService,
                                   IDoctorService doctorService,
                                   AuthenticatedUserService authenticatedUserService) {
        this.patientAppointmentService = patientAppointmentService;
        this.appointmentService = appointmentService;
        this.doctorService = doctorService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping("/patient/appointments")
    public String appointments(Authentication authentication, Model model) {
        var patient = findPatient(authentication);
        var overview = patientAppointmentService.findAppointments(patient);
        model.addAttribute("appointments", overview.appointments());
        model.addAttribute("prescriptionsMap", overview.prescriptionsByAppointment());
        model.addAttribute("patientName", patient.getFullName());
        return "patient/appointments";
    }

    @GetMapping("/patient/appointments/{id}/edit")
    public String editAppointment(@PathVariable Long id, Authentication authentication,
                                  Model model, RedirectAttributes redirectAttributes) {
        var patient = findPatient(authentication);
        var appointment = patientAppointmentService.findChangeableAppointment(id, patient.getId());
        if (appointment.isEmpty()) {
            redirectAttributes.addFlashAttribute("appointmentMessage",
                    "Lịch hẹn không tồn tại hoặc không thể chỉnh sửa.");
            return "redirect:/patient/appointments";
        }
        Appointment current = appointment.get();
        AppointmentRescheduleForm form = new AppointmentRescheduleForm();
        form.setDoctorId(current.getDoctor().getId());
        form.setAppointmentDate(current.getAppointmentDate());
        form.setTimeSlotId(current.getTimeSlot().getId());
        model.addAttribute("appointment", current);
        model.addAttribute("rescheduleForm", form);
        model.addAttribute("doctors", doctorService.findAllDoctors(
                org.springframework.data.domain.Pageable.unpaged()).getContent());
        model.addAttribute("selectedTimeSlotId", current.getTimeSlot().getId());
        return "patient/appointment-edit";
    }

    @PostMapping("/patient/appointments/{id}/edit")
    public String updateAppointment(@PathVariable Long id,
                                    @Valid @ModelAttribute("rescheduleForm") AppointmentRescheduleForm form,
                                    BindingResult bindingResult, Authentication authentication,
                                    Model model, RedirectAttributes redirectAttributes) {
        var patient = findPatient(authentication);
        var appointment = patientAppointmentService.findChangeableAppointment(id, patient.getId());
        if (appointment.isEmpty()) {
            redirectAttributes.addFlashAttribute("appointmentMessage",
                    "Lịch hẹn không tồn tại hoặc không thể chỉnh sửa.");
            return "redirect:/patient/appointments";
        }
        if (bindingResult.hasErrors()) {
            populateEditModel(model, appointment.get(), form);
            return "patient/appointment-edit";
        }
        try {
            if (!appointmentService.reschedulePatientAppointment(id, patient.getId(), form)) {
                redirectAttributes.addFlashAttribute("appointmentMessage", "Không tìm thấy lịch hẹn cần chỉnh sửa.");
            } else {
                redirectAttributes.addFlashAttribute("appointmentMessage", "Đã cập nhật lịch hẹn.");
            }
        } catch (AppointmentSlotUnavailableException ex) {
            bindingResult.rejectValue("timeSlotId", "error.slotBooked", ex.getMessage());
            populateEditModel(model, appointment.get(), form);
            return "patient/appointment-edit";
        } catch (com.codegym.service.appointment.InvalidAppointmentSelectionException ex) {
            bindingResult.rejectValue(ex.getField(), "error.invalidSelection", ex.getMessage());
            populateEditModel(model, appointment.get(), form);
            return "patient/appointment-edit";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("appointmentMessage", ex.getMessage());
        }
        return "redirect:/patient/appointments";
    }

    @PostMapping("/patient/appointments/{id}/cancel")
    public String cancelAppointment(@PathVariable Long id, Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        var patient = findPatient(authentication);
        try {
            if (!appointmentService.cancelPatientAppointment(id, patient.getId())) {
                redirectAttributes.addFlashAttribute("appointmentMessage", "Không tìm thấy lịch hẹn cần hủy.");
            } else {
                redirectAttributes.addFlashAttribute("appointmentMessage",
                        "Đã hủy lịch hẹn. Thông tin vẫn được lưu trong lịch sử của bạn.");
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("appointmentMessage", ex.getMessage());
        }
        return "redirect:/patient/appointments";
    }

    private void populateEditModel(Model model, Appointment appointment, AppointmentRescheduleForm form) {
        model.addAttribute("appointment", appointment);
        model.addAttribute("doctors", doctorService.findAllDoctors(
                org.springframework.data.domain.Pageable.unpaged()).getContent());
        model.addAttribute("selectedTimeSlotId", form.getTimeSlotId());
    }

    private com.codegym.model.User findPatient(Authentication authentication) {
        return authenticatedUserService.findPatientForPortal(authentication);
    }
}
