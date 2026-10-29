package com.codegym.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "doctor_date_schedules", uniqueConstraints =
        @UniqueConstraint(name = "uq_doctor_date_shift", columnNames = {"doctor_id", "schedule_date", "shift"}))
public class DoctorDateSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DoctorWorkShift shift;

    protected DoctorDateSchedule() {}

    public DoctorDateSchedule(Doctor doctor, LocalDate scheduleDate, DoctorWorkShift shift) {
        this.doctor = doctor;
        this.scheduleDate = scheduleDate;
        this.shift = shift;
    }

    public Long getId() { return id; }
    public Doctor getDoctor() { return doctor; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public DoctorWorkShift getShift() { return shift; }
}
