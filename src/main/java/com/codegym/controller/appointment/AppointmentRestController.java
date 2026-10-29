package com.codegym.controller.appointment;

import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.dto.appointment.AppointmentQueryForm;
import com.codegym.service.appointment.IAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDate;
import java.util.List;
import com.codegym.model.TimeSlot;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentRestController {

    @Autowired
    private IAppointmentService appointmentService;

    @GetMapping("/available-slots")
    public ResponseEntity<?> availableSlots(@ModelAttribute AppointmentQueryForm query) {
        List<Map<String, Object>> slots = appointmentService.findAvailableTimeSlots(query.getDoctorId(), query.getDate())
                .stream()
                .map(this::toSlotResponse)
                .toList();
        return ResponseEntity.ok(slots);
    }

    private Map<String, Object> toSlotResponse(TimeSlot slot) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", slot.getId());
        response.put("displayText", slot.getDisplayText());
        return response;
    }

    @Autowired
    private com.codegym.repository.ITimeSlotRepository timeSlotRepository;

    @GetMapping("/all-slots")
    public ResponseEntity<?> allSlots() {
        List<Map<String, Object>> slots = timeSlotRepository.findByActiveTrueOrderByStartTimeAsc()
                .stream()
                .map(this::toSlotResponse)
                .toList();
        return ResponseEntity.ok(slots);
    }

    @GetMapping("/available-doctors")
    public ResponseEntity<?> availableDoctors(@ModelAttribute AppointmentQueryForm query) {
        List<Map<String, Object>> doctors = appointmentService.findAvailableDoctors(
                        query.getSpecialtyId(), query.getDate(), query.getTimeSlotId())
                .stream()
                .map(doc -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", doc.getId());
                    map.put("name", doc.getName());
                    map.put("degree", doc.getDegree());
                    map.put("consultationFee", doc.getConsultationFee() != null ? doc.getConsultationFee() : 300000);
                    map.put("imageUrl", doc.getImageUrl());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(doctors);
    }

    // API Tra cứu thông tin phiếu khám bằng Mã
    @GetMapping("/search")
    public ResponseEntity<?> searchAppointment(@ModelAttribute AppointmentQueryForm query) {
        String bookingCode = query.getBookingCode();
        Appointment appointment = appointmentService.findVerifiedAppointment(bookingCode, query.getPatientPhone());
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
        response.put("paymentStatus", appointment.getPaymentStatus() != null ? appointment.getPaymentStatus().name() : "UNPAID");

        return ResponseEntity.ok(response);
    }

    // API Hủy lịch hẹn bằng AJAX
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelAppointment(@ModelAttribute AppointmentQueryForm query) {
        try {
            appointmentService.cancelAppointmentByBookingCode(query.getBookingCode(), query.getPatientPhone());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }

        return ResponseEntity.ok("Hủy lịch hẹn thành công!");
    }
}