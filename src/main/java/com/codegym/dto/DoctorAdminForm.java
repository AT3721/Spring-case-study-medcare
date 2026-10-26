package com.codegym.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class DoctorAdminForm {
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 100)
    private String degree;

    @DecimalMin(value = "0.0")
    private BigDecimal consultationFee;

    private String bio;

    @NotNull
    private Long specialtyId;

    private boolean active = true;

    @NotBlank
    @Size(max = 50)
    private String username;

    private String password;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }
    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public Long getSpecialtyId() { return specialtyId; }
    public void setSpecialtyId(Long specialtyId) { this.specialtyId = specialtyId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
