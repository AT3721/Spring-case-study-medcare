package com.codegym.repository;

import com.codegym.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ITimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByActiveTrueOrderByStartTimeAsc();
}