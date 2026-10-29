package com.codegym.dto.doctor;

import com.codegym.model.AppointmentStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class DoctorPortalFilterForm {
    private AppointmentStatus status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
    private String clinicalResult;
    private boolean markCompleted;

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getClinicalResult() { return clinicalResult; }
    public void setClinicalResult(String clinicalResult) { this.clinicalResult = clinicalResult; }
    public boolean isMarkCompleted() { return markCompleted; }
    public void setMarkCompleted(boolean markCompleted) { this.markCompleted = markCompleted; }
}
