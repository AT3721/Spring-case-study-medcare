package com.codegym.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SpecialtyAdminForm {
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;

    @Size(max = 255)
    private String iconUrl;

    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
