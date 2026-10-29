package com.codegym.dto.doctor;

public class DoctorDirectoryForm {
    private Long specialtyId;
    private String q;
    private String sortBy = "name";

    public Long getSpecialtyId() { return specialtyId; }
    public void setSpecialtyId(Long specialtyId) { this.specialtyId = specialtyId; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
}
