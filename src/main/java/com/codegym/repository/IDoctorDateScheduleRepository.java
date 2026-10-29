package com.codegym.repository;

import com.codegym.model.DoctorDateSchedule;
import com.codegym.model.DoctorWorkShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface IDoctorDateScheduleRepository extends JpaRepository<DoctorDateSchedule, Long> {
    @Query("select schedule.shift from DoctorDateSchedule schedule " +
            "where schedule.doctor.id = :doctorId and schedule.scheduleDate = :scheduleDate")
    List<DoctorWorkShift> findShiftsByDoctorAndDate(@Param("doctorId") Long doctorId,
                                                    @Param("scheduleDate") LocalDate scheduleDate);

    List<DoctorDateSchedule> findByScheduleDateBetweenOrderByScheduleDateAsc(LocalDate startDate, LocalDate endDate);

    void deleteByDoctorIdAndScheduleDateBetween(Long doctorId, LocalDate startDate, LocalDate endDate);
}
