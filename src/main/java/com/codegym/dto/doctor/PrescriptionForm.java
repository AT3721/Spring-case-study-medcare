package com.codegym.dto.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class PrescriptionForm {

    private Long id;

    @NotNull(message = "Lịch khám không được để trống")
    private Long appointmentId;

    @NotBlank(message = "Tên thuốc không được để trống")
    @Size(max = 200, message = "Tên thuốc tối đa 200 ký tự")
    private String medicineName;

    @NotBlank(message = "Hàm lượng không được để trống (vd: 500mg, 10ml)")
    @Size(max = 100, message = "Hàm lượng tối đa 100 ký tự")
    private String dosage;

    @NotBlank(message = "Số lượng không được để trống (vd: 20 viên, 2 hộp)")
    @Size(max = 50, message = "Số lượng tối đa 50 ký tự")
    private String quantity;

    private String usageInstructions;

    private String doctorAdvice;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate followUpDate;

    public PrescriptionForm() {}

    public PrescriptionForm(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }

    public String getUsageInstructions() { return usageInstructions; }
    public void setUsageInstructions(String usageInstructions) { this.usageInstructions = usageInstructions; }

    public String getDoctorAdvice() { return doctorAdvice; }
    public void setDoctorAdvice(String doctorAdvice) { this.doctorAdvice = doctorAdvice; }

    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }
}
