package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.CalendarAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalendarAccountRepository extends JpaRepository<CalendarAccount, Integer> {

    List<CalendarAccount> findByUserId(Integer userId);
}

