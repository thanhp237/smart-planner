package com.smartplanner.smartplanner.service;

import com.smartplanner.smartplanner.dto.dashboard.*;
import com.smartplanner.smartplanner.model.*;
import com.smartplanner.smartplanner.repository.*;
import com.smartplanner.smartplanner.util.WeekUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final TaskRepository taskRepo;
    private final CourseRepository courseRepo;
    private final StudySessionRepository sessionRepo;
    private final StudyScheduleRepository scheduleRepo;

    public DashboardService(TaskRepository taskRepo,
            CourseRepository courseRepo,
            StudySessionRepository sessionRepo,
            StudyScheduleRepository scheduleRepo) {
        this.taskRepo = taskRepo;
        this.courseRepo = courseRepo;
        this.sessionRepo = sessionRepo;
        this.scheduleRepo = scheduleRepo;
    }

    public DashboardOverviewResponse getDashboardOverview(Integer userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = WeekUtil.weekStart(today, 0);
        LocalDate weekEnd = WeekUtil.weekEnd(weekStart);

        // Task summary
        List<Task> allTasks = taskRepo.findByUserIdOrderByDeadlineDateAsc(userId);
        int totalTasks = allTasks.size();
        int openTasks = (int) allTasks.stream().filter(t -> "OPEN".equals(t.getStatus())).count();
        int doneTasks = (int) allTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        int overdueTasks = (int) allTasks.stream()
                .filter(t -> "OPEN".equals(t.getStatus()) && t.getDeadlineDate().isBefore(today))
                .count();

        // Course summary
        List<Course> allCourses = courseRepo.findByUserIdOrderByCreatedAtDesc(userId);
        int totalCourses = allCourses.size();
        int activeCourses = (int) allCourses.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
        int archivedCourses = (int) allCourses.stream().filter(c -> "ARCHIVED".equals(c.getStatus())).count();

        // Study hours this week
        List<StudySession> weekSessions = sessionRepo
                .findBySchedule_User_IdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
                        userId, weekStart, weekEnd);

        BigDecimal plannedHours = weekSessions.stream()
                .map(s -> BigDecimal.valueOf(s.getDurationMinutes()).divide(BigDecimal.valueOf(60), 2,
                        RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actualHours = weekSessions.stream()
                .filter(s -> s.getActualHoursLogged() != null)
                .map(StudySession::getActualHoursLogged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal completionRate = plannedHours.compareTo(BigDecimal.ZERO) > 0
                ? actualHours.divide(plannedHours, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Study streak
        Integer currentStreak = calculateCurrentStreak(userId);

        // Upcoming deadlines count (next 7 days)
        LocalDate sevenDaysLater = today.plusDays(7);
        int upcomingCount = (int) allTasks.stream()
                .filter(t -> "OPEN".equals(t.getStatus()))
                .filter(t -> !t.getDeadlineDate().isBefore(today) && !t.getDeadlineDate().isAfter(sevenDaysLater))
                .count();

        return new DashboardOverviewResponse(
                new DashboardOverviewResponse.TaskSummary(totalTasks, openTasks, doneTasks, overdueTasks),
                new DashboardOverviewResponse.CourseSummary(totalCourses, activeCourses, archivedCourses),
                new DashboardOverviewResponse.StudyHoursSummary(plannedHours, actualHours, completionRate),
                currentStreak,
                upcomingCount);
    }

    public ProgressMetricsResponse getProgressMetrics(Integer userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            LocalDate today = LocalDate.now();
            startDate = WeekUtil.weekStart(today, 0);
            endDate = WeekUtil.weekEnd(startDate);
        }

        // Task completion rate
        List<Task> tasks = taskRepo.findByUserIdOrderByDeadlineDateAsc(userId);
        int totalTasks = tasks.size();
        int doneTasks = (int) tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        BigDecimal completionRate = totalTasks > 0
                ? BigDecimal.valueOf(doneTasks).divide(BigDecimal.valueOf(totalTasks), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Average hours per day
        List<StudySession> sessions = sessionRepo
                .findBySchedule_User_IdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
                        userId, startDate, endDate);

        BigDecimal totalHours = sessions.stream()
                .filter(s -> s.getActualHoursLogged() != null)
                .map(StudySession::getActualHoursLogged)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        BigDecimal avgHoursPerDay = daysBetween > 0
                ? totalHours.divide(BigDecimal.valueOf(daysBetween), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Tasks by priority
        Map<String, Integer> tasksByPriority = tasks.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getPriority() != null ? t.getPriority() : "MEDIUM",
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        // Tasks by status
        Map<String, Integer> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(
                        Task::getStatus,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        // Session counts
        int totalSessions = sessions.size();
        int completedSessions = (int) sessions.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();

        return new ProgressMetricsResponse(
                startDate,
                endDate,
                completionRate,
                avgHoursPerDay,
                tasksByPriority,
                tasksByStatus,
                totalSessions,
                completedSessions);
    }

    public List<UpcomingDeadlineResponse> getUpcomingDeadlines(Integer userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);

        return taskRepo.findByUserIdOrderByDeadlineDateAsc(userId).stream()
                .filter(t -> "OPEN".equals(t.getStatus()))
                .filter(t -> !t.getDeadlineDate().isBefore(today) && !t.getDeadlineDate().isAfter(endDate))
                .map(t -> {
                    int daysRemaining = (int) ChronoUnit.DAYS.between(today, t.getDeadlineDate());
                    BigDecimal completion = calculateTaskCompletion(t);
                    String courseName = t.getCourse() != null ? t.getCourse().getName() : "Unknown";

                    return new UpcomingDeadlineResponse(
                            t.getId(),
                            t.getTitle(),
                            courseName,
                            t.getDeadlineDate(),
                            daysRemaining,
                            t.getPriority(),
                            completion);
                })
                .collect(Collectors.toList());
    }

    public StudyStreakResponse getStudyStreak(Integer userId) {
        List<LocalDate> completedDates = sessionRepo.findDistinctCompletedSessionDates(userId);

        if (completedDates.isEmpty()) {
            return new StudyStreakResponse(0, 0, 0, null);
        }

        int currentStreak = calculateCurrentStreak(userId);
        int longestStreak = calculateLongestStreak(completedDates);
        int totalStudyDays = completedDates.size();
        LocalDate lastStudyDate = completedDates.isEmpty() ? null : completedDates.get(0);

        return new StudyStreakResponse(currentStreak, longestStreak, totalStudyDays, lastStudyDate);
    }

    public List<CourseProgressResponse> getCourseProgress(Integer userId) {
        List<Course> courses = courseRepo.findByUserIdOrderByCreatedAtDesc(userId);

        return courses.stream().map(course -> {
            List<Task> courseTasks = taskRepo.findByUserIdOrderByDeadlineDateAsc(userId).stream()
                    .filter(t -> t.getCourse() != null && t.getCourse().getId().equals(course.getId()))
                    .collect(Collectors.toList());

            int totalTasks = courseTasks.size();
            int completedTasks = (int) courseTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
            BigDecimal completionPercentage = totalTasks > 0
                    ? BigDecimal.valueOf(completedTasks).divide(BigDecimal.valueOf(totalTasks), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            return new CourseProgressResponse(
                    course.getId(),
                    course.getName(),
                    totalTasks,
                    completedTasks,
                    completionPercentage,
                    course.getStatus());
        }).collect(Collectors.toList());
    }

    // Helper methods
    private Integer calculateCurrentStreak(Integer userId) {
        List<LocalDate> completedDates = sessionRepo.findDistinctCompletedSessionDates(userId);

        if (completedDates.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        LocalDate checkDate = today;

        // Check if there's a session today or yesterday (to allow for ongoing streaks)
        if (!completedDates.contains(today) && !completedDates.contains(today.minusDays(1))) {
            return 0;
        }

        int streak = 0;
        while (completedDates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }

        return streak;
    }

    private Integer calculateLongestStreak(List<LocalDate> completedDates) {
        if (completedDates.isEmpty()) {
            return 0;
        }

        // Sort dates in ascending order
        List<LocalDate> sortedDates = new ArrayList<>(completedDates);
        Collections.sort(sortedDates);

        int longestStreak = 1;
        int currentStreak = 1;

        for (int i = 1; i < sortedDates.size(); i++) {
            if (ChronoUnit.DAYS.between(sortedDates.get(i - 1), sortedDates.get(i)) == 1) {
                currentStreak++;
                longestStreak = Math.max(longestStreak, currentStreak);
            } else {
                currentStreak = 1;
            }
        }

        return longestStreak;
    }

    private BigDecimal calculateTaskCompletion(Task task) {
        if (task.getEstimatedHours() == null || task.getEstimatedHours().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal completed = task.getEstimatedHours().subtract(
                task.getRemainingHours() != null ? task.getRemainingHours() : BigDecimal.ZERO);

        return completed.divide(task.getEstimatedHours(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
