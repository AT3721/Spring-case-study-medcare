package com.codegym.model;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "time_slots")
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "display_text", nullable = false, length = 50)
    private String displayText;

    @Column(nullable = false)
    private boolean active = true;

    public TimeSlot() {}

    public TimeSlot(LocalTime startTime, LocalTime endTime, String displayText) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.displayText = displayText;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}