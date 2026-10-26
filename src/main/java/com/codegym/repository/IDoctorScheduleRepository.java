package com.codegym.repository;

import com.codegym.model.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface IDoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByDoctorId(Long doctorId);

    @Query("select schedule.timeSlot.id from DoctorSchedule schedule " +
            "where schedule.doctor.id = :doctorId and schedule.dayOfWeek = :dayOfWeek")
    List<Long> findTimeSlotIdsByDoctorAndDay(@Param("doctorId") Long doctorId,
                                              @Param("dayOfWeek") DayOfWeek dayOfWeek);

    @Modifying
    @Query("delete from DoctorSchedule schedule where schedule.doctor.id = :doctorId")
    void deleteByDoctorId(@Param("doctorId") Long doctorId);
}
