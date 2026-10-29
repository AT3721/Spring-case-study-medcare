package com.codegym.dto.appointment;

import com.codegym.model.AppointmentStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.YearMonth;

public class AdminAppointmentForm {
    private Long id;
    private AppointmentStatus status;
    private AppointmentStatus statusFilter;
    private Long specialtyId;
    private String clinicalResult;
    private boolean clinicalResultChanged;
    @DateTimeFormat(pattern = "yyyy-MM")
    private YearMonth month;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public AppointmentStatus getStatusFilter() { return statusFilter; }
    public void setStatusFilter(AppointmentStatus statusFilter) { this.statusFilter = statusFilter; }
    public Long getSpecialtyId() { return specialtyId; }
    public void setSpecialtyId(Long specialtyId) { this.specialtyId = specialtyId; }
    public String getClinicalResult() { return clinicalResult; }
    public void setClinicalResult(String clinicalResult) { this.clinicalResult = clinicalResult; }
    public boolean isClinicalResultChanged() { return clinicalResultChanged; }
    public void setClinicalResultChanged(boolean clinicalResultChanged) { this.clinicalResultChanged = clinicalResultChanged; }
    public YearMonth getMonth() { return month; }
    public void setMonth(YearMonth month) { this.month = month; }
}
