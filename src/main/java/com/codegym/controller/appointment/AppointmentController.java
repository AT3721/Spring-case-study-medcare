package com.codegym.controller.appointment;

import com.codegym.dto.appointment.AppointmentDTO;
import com.codegym.dto.appointment.AppointmentRescheduleForm;
import com.codegym.dto.appointment.AppointmentQueryForm;
import com.codegym.dto.appointment.AdminAppointmentForm;
import com.codegym.dto.appointment.AppointmentDTO;
import com.codegym.dto.appointment.AppointmentRescheduleForm;
import com.codegym.dto.appointment.AppointmentQueryForm;
import com.codegym.dto.appointment.AdminAppointmentForm;
import com.codegym.model.Appointment;
import com.codegym.service.appointment.IAppointmentService;
import com.codegym.service.doctor.IDoctorService;
import com.codegym.service.appointment.AppointmentSlotUnavailableException;
import com.codegym.service.appointment.InvalidAppointmentSelectionException;
import com.codegym.model.User;
import com.codegym.service.auth.AuthenticatedUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/appointments")
public class AppointmentController {

    @Autowired
    private IDoctorService doctorService;

    @Autowired
    private IAppointmentService appointmentService;

    @Autowired
    private com.codegym.repository.ISpecialtyRepository specialtyRepository;

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @GetMapping("/book")
    public String showBookingForm(@ModelAttribute AppointmentQueryForm query,
                                  Authentication authentication, Model model) {
        Long doctorId = query.getDoctorId();
        java.time.LocalDate appointmentDate = query.getAppointmentDate();
        AppointmentDTO dto = new AppointmentDTO();
        com.codegym.model.Doctor preSelectedDoctor = null;

        if (doctorId != null) {
            com.codegym.model.Doctor foundDoctor = doctorService.findDoctorById(doctorId);
            if (foundDoctor != null && foundDoctor.isActive()) {
                preSelectedDoctor = foundDoctor;
                dto.setDoctorId(doctorId);
            }
        }
        if (appointmentDate != null && appointmentDate.isAfter(java.time.LocalDate.now())) {
            dto.setAppointmentDate(appointmentDate);
        }
        User patient = authenticatedUserService.findPatientForBooking(authentication);
        if (patient != null) {
            dto.setPatientName(patient.getFullName());
            dto.setPatientEmail(patient.getEmail());
        }

        String flow = (preSelectedDoctor != null) ? "doctor" : "wizard";

        model.addAttribute("appointmentDTO", dto);
        model.addAttribute("preSelectedDoctor", preSelectedDoctor);
        model.addAttribute("flow", flow);
        model.addAttribute("specialties", specialtyRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("doctors", doctorService.findAllDoctors(org.springframework.data.domain.Pageable.unpaged()).getContent());
        model.addAttribute("selectedTimeSlotId", null);

        return "appointment/booking";
    }

    @PostMapping("/book")
    public String processBooking(@Valid @ModelAttribute("appointmentDTO") AppointmentDTO dto,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes,
                                 Authentication authentication) {
        User patient = authenticatedUserService.findPatientForBooking(authentication);
        if (patient != null) {
            dto.setPatientName(patient.getFullName());
            dto.setPatientEmail(patient.getEmail());
        }

        // Kiểm tra validation cơ bản (tên, sdt, ngày...)
        if (bindingResult.hasErrors()) {
            populateBookingModel(model, dto);
            return "appointment/booking";
        }

        Appointment savedAppointment;
        try {
            savedAppointment = appointmentService.createAppointment(dto, patient);
        } catch (AppointmentSlotUnavailableException ex) {
            bindingResult.rejectValue("timeSlotId", "error.slotBooked", ex.getMessage());
            populateBookingModel(model, dto);
            return "appointment/booking";
        } catch (InvalidAppointmentSelectionException ex) {
            bindingResult.rejectValue(ex.getField(), "error.invalidSelection", ex.getMessage());
            populateBookingModel(model, dto);
            return "appointment/booking";
        }
        redirectAttributes.addFlashAttribute("bookingSuccessCode", savedAppointment.getBookingCode());
        redirectAttributes.addFlashAttribute("paymentMethod", savedAppointment.getPaymentMethod());
        redirectAttributes.addFlashAttribute("appointment", savedAppointment);

        return "redirect:/payment/pay/" + savedAppointment.getBookingCode();
    }

    private void populateBookingModel(Model model, AppointmentDTO dto) {
        com.codegym.model.Doctor preSelectedDoctor = null;
        if (dto.getDoctorId() != null) {
            com.codegym.model.Doctor foundDoctor = doctorService.findDoctorById(dto.getDoctorId());
            if (foundDoctor != null && foundDoctor.isActive()) {
                preSelectedDoctor = foundDoctor;
            }
        }
        model.addAttribute("flow", preSelectedDoctor != null ? "doctor" : "wizard");
        model.addAttribute("preSelectedDoctor", preSelectedDoctor);
        model.addAttribute("specialties", specialtyRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("doctors", doctorService.findAllDoctors(org.springframework.data.domain.Pageable.unpaged()).getContent());
        model.addAttribute("selectedTimeSlotId", dto.getTimeSlotId());
    }

    @GetMapping("/success")
    public String bookingSuccess() {
        return "appointment/success";
    }

}