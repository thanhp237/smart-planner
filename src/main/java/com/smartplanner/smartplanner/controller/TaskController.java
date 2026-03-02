package com.smartplanner.smartplanner.controller;

import com.smartplanner.smartplanner.dto.task.*;
import com.smartplanner.smartplanner.model.Course;
import com.smartplanner.smartplanner.model.Task;
import com.smartplanner.smartplanner.model.User;
import com.smartplanner.smartplanner.repository.CourseRepository;
import com.smartplanner.smartplanner.repository.StudySessionRepository;
import com.smartplanner.smartplanner.repository.TaskRepository;
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
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository taskRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final StudySessionRepository sessionRepository;

    public TaskController(TaskRepository taskRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            StudySessionRepository sessionRepository) {
        this.taskRepository = taskRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public List<TaskResponse> list(@RequestParam(required = false) Integer courseId,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        List<Task> tasks = (courseId == null)
                ? taskRepository.findByUserIdOrderByDeadlineDateAsc(userId)
                : taskRepository.findByUserIdAndCourseIdOrderByDeadlineDateAsc(userId, courseId);

        return tasks.stream()
                .filter(TaskController::isVisible)
                .map(TaskController::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        Task t = taskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!isVisible(t)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }

        return toResponse(t);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody TaskCreateRequest req, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        // Load user entity
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Course c = null;
        if (req.getCourseId() != null) {
            c = courseRepository.findByIdAndUserId(req.getCourseId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid courseId"));
            if (Boolean.TRUE.equals(c.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid courseId");
            }
        }

        Task t = new Task();
        t.setUser(user);
        t.setCourse(c);
        t.setTitle(req.getTitle().trim());
        t.setDescription(req.getDescription());
        t.setType(req.getType());
        t.setPriority(req.getPriority());
        t.setDeadlineDate(req.getDeadlineDate());
        t.setEstimatedHours(req.getEstimatedHours());
        t.setRemainingHours(req.getEstimatedHours()); // default remaining = estimated
        t.setStatus("OPEN");
        t.setCreatedAt(LocalDateTime.now());

        return toResponse(taskRepository.save(t));
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Integer id,
            @Valid @RequestBody TaskUpdateRequest req,
            HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        Task t = taskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!isVisible(t)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }

        Course newCourse = null;
        if (req.getCourseId() != null) {
            newCourse = courseRepository.findByIdAndUserId(req.getCourseId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid courseId"));
            if (Boolean.TRUE.equals(newCourse.getIsDeleted())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid courseId");
            }
        }

        t.setCourse(newCourse);
        t.setTitle(req.getTitle().trim());
        t.setDescription(req.getDescription());
        t.setType(req.getType());
        t.setPriority(req.getPriority());
        t.setDeadlineDate(req.getDeadlineDate());
        t.setEstimatedHours(req.getEstimatedHours());
        t.setRemainingHours(req.getRemainingHours());
        t.setStatus(req.getStatus());
        t.setUpdatedAt(LocalDateTime.now());

        return toResponse(taskRepository.save(t));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, HttpSession session) {
        Integer userId = SessionUtil.requireUserId(session);

        Task t = taskRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        // Soft delete: mark as CANCELED and remove upcoming sessions
        t.setStatus("CANCELED");
        t.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(t);

        // Remove sessions for this task except COMPLETED to avoid losing history
        sessionRepository.deleteByTaskIdAndStatusNotCompleted(id);
    }

    private static TaskResponse toResponse(Task t) {
        return new TaskResponse(
                t.getId(),
                t.getCourse() != null ? t.getCourse().getId() : null,
                t.getTitle(),
                t.getDescription(),
                t.getType(),
                t.getPriority(),
                t.getDeadlineDate(),
                t.getEstimatedHours(),
                t.getRemainingHours(),
                t.getStatus());
    }

    private static boolean isVisible(Task t) {
        if (t == null) return false;
        if ("CANCELED".equalsIgnoreCase(t.getStatus())) return false;
        Course c = t.getCourse();
        if (c != null && Boolean.TRUE.equals(c.getIsDeleted())) return false;
        return true;
    }
}
