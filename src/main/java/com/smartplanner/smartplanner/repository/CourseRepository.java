package com.smartplanner.smartplanner.repository;

import com.smartplanner.smartplanner.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Integer> {
    List<Course> findByUserIdOrderByCreatedAtDesc(Integer userId);
    List<Course> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Integer userId);
    Optional<Course> findByIdAndUserId(Integer id, Integer userId);
    boolean existsByIdAndUserId(Integer id, Integer userId);
}
