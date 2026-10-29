package com.codegym.dto.admin;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class CatalogScheduleForm {
    @DateTimeFormat(pattern = "yyyy-MM")
    private YearMonth month;
    private Long specialtyId;
    private List<String> shiftSelections = new ArrayList<>();

    public YearMonth getMonth() { return month; }
    public void setMonth(YearMonth month) { this.month = month; }
    public Long getSpecialtyId() { return specialtyId; }
    public void setSpecialtyId(Long specialtyId) { this.specialtyId = specialtyId; }
    public List<String> getShiftSelections() { return shiftSelections; }
    public void setShiftSelections(List<String> shiftSelections) { this.shiftSelections = shiftSelections; }
}
