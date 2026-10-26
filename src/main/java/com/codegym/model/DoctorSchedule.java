package com.codegym.model;

import jakarta.persistence.*;

import java.time.DayOfWeek;

@Entity
@Table(name = "doctor_schedules", uniqueConstraints =
        @UniqueConstraint(name = "uq_doctor_schedule", columnNames = {"doctor_id", "day_of_week", "time_slot_id"}))
public class DoctorSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    public DoctorSchedule() {}

    public DoctorSchedule(Doctor doctor, DayOfWeek dayOfWeek, TimeSlot timeSlot) {
        this.doctor = doctor;
        this.dayOfWeek = dayOfWeek;
        this.timeSlot = timeSlot;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public TimeSlot getTimeSlot() { return timeSlot; }
    public void setTimeSlot(TimeSlot timeSlot) { this.timeSlot = timeSlot; }
}
