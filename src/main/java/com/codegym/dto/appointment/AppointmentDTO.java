package com.codegym.dto.appointment;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class AppointmentDTO {

    @NotNull(message = "Vui lòng chọn bác sĩ")
    private Long doctorId;

    @NotNull(message = "Vui lòng chọn ca khám")
    private Long timeSlotId;

    @NotNull(message = "Vui lòng chọn ngày khám")
    @Future(message = "Ngày khám phải là một ngày trong tương lai")
    private LocalDate appointmentDate;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    private String patientName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0[35789][0-9]{8}$", message = "Số điện thoại không đúng định dạng Việt Nam (10 số)")
    private String patientPhone;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String patientEmail;

    private String notes;

    private com.codegym.model.PaymentMethod paymentMethod = com.codegym.model.PaymentMethod.VNPAY;

    public AppointmentDTO() {}

    // Getters and Setters
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }

    public Long getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(Long timeSlotId) { this.timeSlotId = timeSlotId; }

    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }

    public String getPatientEmail() { return patientEmail; }
    public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public com.codegym.model.PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(com.codegym.model.PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
}