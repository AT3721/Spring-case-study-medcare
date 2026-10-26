package com.codegym.controller;

import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.service.IAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import com.codegym.model.TimeSlot;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentRestController {

    @Autowired
    private IAppointmentService appointmentService;

    @Autowired
    private IAppointmentRepository appointmentRepository;

    @GetMapping("/available-slots")
    public ResponseEntity<?> availableSlots(@RequestParam("doctorId") Long doctorId,
                                            @RequestParam("date") String dateValue) {
        try {
            LocalDate date = LocalDate.parse(dateValue);
            List<Map<String, Object>> slots = appointmentService.findAvailableTimeSlots(doctorId, date)
                    .stream()
                    .map(this::toSlotResponse)
                    .toList();
            return ResponseEntity.ok(slots);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().body("Ngày khám không đúng định dạng.");
        }
    }

    private Map<String, Object> toSlotResponse(TimeSlot slot) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", slot.getId());
        response.put("displayText", slot.getDisplayText());
        return response;
    }

    // API Tra cứu thông tin phiếu khám bằng Mã
    @GetMapping("/search")
    public ResponseEntity<?> searchAppointment(@RequestParam("bookingCode") String bookingCode,
                                               @RequestParam("patientPhone") String patientPhone) {
        Appointment appointment = findVerifiedAppointment(bookingCode, patientPhone);
        if (appointment == null) {
            return ResponseEntity.badRequest().body("Không tìm thấy thông tin phiếu khám với mã: " + bookingCode);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("bookingCode", appointment.getBookingCode());
        response.put("patientName", appointment.getPatientName());
        response.put("patientPhone", appointment.getPatientPhone());
        response.put("doctorName", appointment.getDoctor().getName());
        response.put("specialtyName", appointment.getDoctor().getSpecialty().getName());
        response.put("appointmentDate", appointment.getAppointmentDate().toString());
        response.put("timeSlot", appointment.getTimeSlot().getDisplayText());
        response.put("status", appointment.getStatus().name());

        return ResponseEntity.ok(response);
    }

    // API Hủy lịch hẹn bằng AJAX
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelAppointment(@RequestParam("bookingCode") String bookingCode,
                                               @RequestParam("patientPhone") String patientPhone) {
        Appointment appointment = findVerifiedAppointment(bookingCode, patientPhone);
        if (appointment == null) {
            return ResponseEntity.badRequest().body("Không tìm thấy phiếu khám với mã và số điện thoại đã nhập!");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return ResponseEntity.badRequest().body("Lịch hẹn này đã bị hủy trước đó!");
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            return ResponseEntity.badRequest().body("Lịch hẹn đã hoàn thành, không thể hủy!");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);

        return ResponseEntity.ok("Hủy lịch hẹn thành công!");
    }

    private Appointment findVerifiedAppointment(String bookingCode, String patientPhone) {
        Appointment appointment = appointmentService.findByBookingCode(bookingCode.trim().toUpperCase());
        if (appointment == null || !Objects.equals(appointment.getPatientPhone(), patientPhone.trim())) {
            return null;
        }
        return appointment;
    }
}