package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.course.*;
import com.smartplanner.smartplanner.model.Course;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.CourseRepository;
import com.smartplanner.smartplanner.repository.UserRepository;
import com.smartplanner.smartplanner.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseController(CourseRepository courseRepository,
            UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<CourseResponse> list(HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        return courseRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(CourseController::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);
        Course c = courseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        return toResponse(c);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@Valid @RequestBody CourseCreateRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        // Load user entity
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Course c = new Course();
        c.setUser(user);
        c.setName(req.getName().trim());
        c.setPriority(req.getPriority());
        c.setDeadlineDate(req.getDeadlineDate());
        c.setTotalHours(req.getTotalHours());
        c.setStatus("ACTIVE");
        c.setCreatedAt(LocalDateTime.now());

        Course saved = courseRepository.save(c);
        return toResponse(saved);
    }

    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable Integer id,
            @Valid @RequestBody CourseUpdateRequest req,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        Course c = courseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        c.setName(req.getName().trim());
        c.setPriority(req.getPriority());
        c.setDeadlineDate(req.getDeadlineDate());
        c.setTotalHours(req.getTotalHours());
        c.setStatus(req.getStatus());
        c.setUpdatedAt(LocalDateTime.now());

        return toResponse(courseRepository.save(c));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        Course c = courseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        courseRepository.delete(c);
    }

    private static CourseResponse toResponse(Course c) {
        return new CourseResponse(
                c.getId(),
                c.getName(),
                c.getPriority(),
                c.getDeadlineDate(),
                c.getTotalHours(),
                c.getStatus());
    }
}
