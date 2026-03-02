package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Integer> {
    List<AvailabilitySlot> findByUserIdOrderByDayOfWeekAscStartTimeAsc(Integer userId);
    List<AvailabilitySlot> findByUserIdAndDayOfWeekOrderByStartTimeAsc(Integer userId, Short dayOfWeek);
    Optional<AvailabilitySlot> findByIdAndUserId(Integer id, Integer userId);
}
