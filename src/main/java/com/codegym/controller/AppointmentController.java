package com.codegym.controller;

import com.codegym.dto.AppointmentDTO;
import com.codegym.model.Appointment;
import com.codegym.service.IAppointmentService;
import com.codegym.service.IDoctorService;
import com.codegym.service.AppointmentSlotUnavailableException;
import com.codegym.service.InvalidAppointmentSelectionException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping("/book")
    public String showBookingForm(@RequestParam(name = "doctorId", required = false) Long doctorId, Model model) {
        AppointmentDTO dto = new AppointmentDTO();
        if (doctorId != null) {
            dto.setDoctorId(doctorId);
        }

        model.addAttribute("appointmentDTO", dto);
        model.addAttribute("doctors", doctorService.findAllDoctors(org.springframework.data.domain.Pageable.unpaged()).getContent());
        model.addAttribute("selectedTimeSlotId", null);

        return "appointment/booking";
    }

    @PostMapping("/book")
    public String processBooking(@Valid @ModelAttribute("appointmentDTO") AppointmentDTO dto,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        // Kiểm tra validation cơ bản (tên, sdt, ngày...)
        if (bindingResult.hasErrors()) {
            model.addAttribute("doctors", doctorService.findAllDoctors(org.springframework.data.domain.Pageable.unpaged()).getContent());
            model.addAttribute("selectedTimeSlotId", dto.getTimeSlotId());
            return "appointment/booking";
        }

        // Kiểm tra ca khám đã có ai đặt chưa (Custom Validation)
        if (appointmentService.isSlotBooked(dto.getDoctorId(), dto.getAppointmentDate(), dto.getTimeSlotId())) {
            bindingResult.rejectValue("timeSlotId", "error.slotBooked", "Bác sĩ đã có lịch khám vào khung giờ này. Vui lòng chọn ca khác!");
            model.addAttribute("doctors", doctorService.findAllDoctors(org.springframework.data.domain.Pageable.unpaged()).getContent());
            model.addAttribute("selectedTimeSlotId", dto.getTimeSlotId());
            return "appointment/booking";
        }

        Appointment savedAppointment;
        try {
            savedAppointment = appointmentService.createAppointment(dto);
        } catch (AppointmentSlotUnavailableException ex) {
            bindingResult.rejectValue("timeSlotId", "error.slotBooked", ex.getMessage());
            model.addAttribute("doctors", doctorService.findAllDoctors(org.springframework.data.domain.Pageable.unpaged()).getContent());
            model.addAttribute("selectedTimeSlotId", dto.getTimeSlotId());
            return "appointment/booking";
        } catch (InvalidAppointmentSelectionException ex) {
            bindingResult.rejectValue(ex.getField(), "error.invalidSelection", ex.getMessage());
            model.addAttribute("doctors", doctorService.findAllDoctors(org.springframework.data.domain.Pageable.unpaged()).getContent());
            model.addAttribute("selectedTimeSlotId", dto.getTimeSlotId());
            return "appointment/booking";
        }
        redirectAttributes.addFlashAttribute("bookingSuccessCode", savedAppointment.getBookingCode());

        return "redirect:/appointments/success";
    }

    @GetMapping("/success")
    public String bookingSuccess() {
        return "appointment/success";
    }
}