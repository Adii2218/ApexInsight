package com.apexinsight.service;

import com.apexinsight.data.Database;
import com.apexinsight.data.EvaluationDao;
import com.apexinsight.data.GoalDao;
import com.apexinsight.data.UserDao;
import com.apexinsight.model.Employee;
import com.apexinsight.model.Evaluation;
import com.apexinsight.model.Goal;
import com.apexinsight.model.Manager;
import com.apexinsight.model.User;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public final class ApexInsightService {
    private static final List<String> GOAL_STATUSES = List.of("Not started", "In progress", "At risk", "Completed");
    private final UserDao users;
    private final EvaluationDao evaluations;
    private final GoalDao goals;

    public static List<String> goalStatuses() {
        return GOAL_STATUSES;
    }

    public ApexInsightService(Database database) {
        users = new UserDao(database);
        evaluations = new EvaluationDao(database);
        goals = new GoalDao(database);
    }

    public User login(String username, char[] password) throws AppException {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            throw new ValidationException("Enter your username and password.");
        }
        try {
            return users.authenticate(username.trim(), password)
                    .orElseThrow(() -> new AuthorizationException("The username or password is incorrect."));
        } catch (SQLException exception) {
            throw new AppException("Could not sign in. Check the database and try again.", exception);
        }
    }

    public List<Employee> getEmployees(User viewer) throws AppException {
        requireManager(viewer);
        try {
            return users.findEmployees();
        } catch (SQLException exception) {
            throw new AppException("Could not load employees.", exception);
        }
    }

    public List<Evaluation> getEvaluations(User viewer, int employeeId) throws AppException {
        if (viewer == null || (viewer instanceof Employee && viewer.getId() != employeeId)) {
            throw new AppException("You can only view your own performance reviews.");
        }
        try {
            return evaluations.findForEmployee(employeeId);
        } catch (SQLException exception) {
            throw new AppException("Could not load performance reviews.", exception);
        }
    }

    public void addEvaluation(User actor, int employeeId, int rating, String feedback) throws AppException {
        requireManager(actor);
        String cleanFeedback = feedback == null ? "" : feedback.trim();
        if (rating < 1 || rating > 5) {
            throw new ValidationException("Choose a rating from 1 to 5.");
        }
        if (cleanFeedback.isBlank()) {
            throw new ValidationException("Write feedback before saving the review.");
        }
        try {
            requireEmployee(employeeId);
            evaluations.add(employeeId, actor.getId(), rating, cleanFeedback);
        } catch (SQLException exception) {
            throw new AppException("Could not save the review.", exception);
        }
    }

    public List<Evaluation> getAllEvaluations(User viewer) throws AppException {
        requireManager(viewer);
        try {
            return evaluations.findAll();
        } catch (SQLException exception) {
            throw new AppException("Could not load the review history.", exception);
        }
    }

    public void updateEvaluation(User actor, int evaluationId, int employeeId, int rating, String feedback)
            throws AppException {
        requireManager(actor);
        validateReview(rating, feedback);
        try {
            requireEmployee(employeeId);
            evaluations.update(evaluationId, employeeId, rating, feedback.trim());
        } catch (SQLException exception) {
            throw new AppException("Could not update the review.", exception);
        }
    }

    public void deleteEvaluation(User actor, int evaluationId) throws AppException {
        requireManager(actor);
        try {
            evaluations.delete(evaluationId);
        } catch (SQLException exception) {
            throw new AppException("Could not delete the review.", exception);
        }
    }

    public List<Goal> getGoals(User viewer) throws AppException {
        if (viewer == null) {
            throw new AppException("Sign in to view goals.");
        }
        try {
            return viewer instanceof Manager ? goals.findAll() : goals.findForEmployee(viewer.getId());
        } catch (SQLException exception) {
            throw new AppException("Could not load goals.", exception);
        }
    }

    public void saveGoal(User actor, int goalId, int employeeId, String title, String description,
                        LocalDate dueDate, String status, int progress) throws AppException {
        requireManager(actor);
        if (title == null || title.isBlank()) {
            throw new ValidationException("Enter a goal title.");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("Enter a goal description.");
        }
        if (dueDate == null || status == null || status.isBlank()) {
            throw new ValidationException("Choose a due date and status.");
        }
        if (!GOAL_STATUSES.contains(status.trim())) {
            throw new ValidationException("Choose a valid goal status.");
        }
        if (progress < 0 || progress > 100) {
            throw new ValidationException("Progress must be between 0 and 100.");
        }
        try {
            requireEmployee(employeeId);
            Employee employee = users.findEmployees().stream()
                    .filter(candidate -> candidate.getId() == employeeId)
                    .findFirst().orElseThrow(() -> new ValidationException("Select a valid employee."));
            String cleanStatus = status.trim();
            String consistentStatus = progress == 100 ? "Completed"
                    : progress > 0 && cleanStatus.equals("Not started") ? "In progress" : cleanStatus;
            int consistentProgress = cleanStatus.equals("Completed") ? 100 : progress;
            goals.save(new Goal(goalId, employeeId, employee.getFullName(), title.trim(),
                    description.trim(), dueDate, consistentStatus, consistentProgress));
        } catch (SQLException exception) {
            throw new AppException("Could not save the goal.", exception);
        }
    }

    public void deleteGoal(User actor, int goalId) throws AppException {
        requireManager(actor);
        try {
            goals.delete(goalId);
        } catch (SQLException exception) {
            throw new AppException("Could not delete the goal.", exception);
        }
    }

    public void updateGoalProgress(User actor, int goalId, int progress) throws AppException {
        if (actor == null) throw new AuthorizationException("Sign in to update goal progress.");
        if (progress < 0 || progress > 100) throw new ValidationException("Progress must be between 0 and 100.");
        try {
            Goal goal = goals.findById(goalId);
            if (goal == null) throw new ValidationException("This goal no longer exists.");
            if (!(actor instanceof Manager) && goal.employeeId() != actor.getId()) {
                throw new AuthorizationException("You can only update your own goals.");
            }
            String status = progress == 100 ? "Completed"
                    : progress > 0 && goal.status().equals("Not started") ? "In progress" : goal.status();
            if (!goals.updateEmployeeProgress(goalId, goal.employeeId(), progress, status)) {
                throw new AppException("This goal could not be updated.");
            }
        } catch (SQLException exception) {
            throw new AppException("Could not update goal progress.", exception);
        }
    }

    public Employee createEmployee(User actor, String username, String fullName, String department, String jobTitle,
                                    char[] password) throws AppException {
        requireManager(actor);
        validateEmployee(username, fullName, department, jobTitle, password);
        try {
            return users.createEmployee(username.trim().toLowerCase(java.util.Locale.ROOT), fullName.trim(),
                    department.trim(), jobTitle.trim(), password);
        } catch (SQLException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("UNIQUE constraint failed")) {
                throw new ValidationException("That username is already in use.");
            }
            throw new AppException("Could not create the employee account.", exception);
        }
    }

    public Employee updateEmployee(User actor, int id, String username, String fullName,
                                   String department, String jobTitle) throws AppException {
        requireManager(actor);
        validateEmployee(username, fullName, department, jobTitle, null);
        try {
            return users.updateEmployee(id, username.trim().toLowerCase(java.util.Locale.ROOT), fullName.trim(),
                    department.trim(), jobTitle.trim());
        } catch (SQLException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("UNIQUE constraint failed")) {
                throw new ValidationException("That username is already in use.");
            }
            throw new AppException("Could not update the employee account.", exception);
        }
    }

    public void deleteEmployee(User actor, int id) throws AppException {
        requireManager(actor);
        try {
            if (!users.deleteEmployee(id)) throw new ValidationException("This employee no longer exists.");
        } catch (SQLException exception) {
            throw new AppException("Could not delete the employee account.", exception);
        }
    }

    public void resetEmployeePassword(User actor, int id, char[] password) throws AppException {
        requireManager(actor);
        validatePassword(password);
        try {
            if (!users.isEmployee(id)) {
                throw new ValidationException("This employee no longer exists.");
            }
            if (!users.changePassword(id, password)) {
                throw new ValidationException("This employee no longer exists.");
            }
        } catch (SQLException exception) {
            throw new AppException("Could not reset the employee's password.", exception);
        }
    }

    public void changeOwnPassword(User actor, char[] currentPassword, char[] newPassword) throws AppException {
        if (actor == null) throw new AuthorizationException("Sign in to change your password.");
        validatePassword(newPassword);
        try {
            users.authenticate(actor.getUsername(), currentPassword)
                    .orElseThrow(() -> new AuthorizationException("Your current password is incorrect."));
            if (!users.changePassword(actor.getId(), newPassword)) {
                throw new AuthorizationException("Your account could not be updated.");
            }
        } catch (SQLException exception) {
            throw new AppException("Could not change your password.", exception);
        }
    }

    private static void validateReview(int rating, String feedback) throws ValidationException {
        if (rating < 1 || rating > 5) throw new ValidationException("Choose a rating from 1 to 5.");
        if (feedback == null || feedback.isBlank()) throw new ValidationException("Write feedback before saving.");
    }

    private static void validateEmployee(String username, String fullName, String department,
                                         String jobTitle, char[] password) throws ValidationException {
        if (username == null || !username.trim().matches("[A-Za-z0-9._-]{3,30}")) {
            throw new ValidationException("Use a username with 3–30 letters, numbers, dots, dashes or underscores.");
        }
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 100) {
            throw new ValidationException("Enter an employee name (up to 100 characters).");
        }
        if (department == null || department.isBlank() || department.trim().length() > 100) {
            throw new ValidationException("Enter a department (up to 100 characters).");
        }
        if (jobTitle == null || jobTitle.isBlank() || jobTitle.trim().length() > 100) {
            throw new ValidationException("Enter a job title (up to 100 characters).");
        }
        if (password != null) validatePassword(password);
    }

    private static void validatePassword(char[] password) throws ValidationException {
        if (password == null || password.length < 8 || password.length > 128) {
            throw new ValidationException("Choose a password between 8 and 128 characters.");
        }
    }

    private void requireEmployee(int employeeId) throws SQLException, AppException {
        if (!users.isEmployee(employeeId)) {
            throw new ValidationException("Select a valid employee.");
        }
    }

    private static void requireManager(User actor) throws AppException {
        if (!(actor instanceof Manager)) {
            throw new AuthorizationException("Only managers can create or update reviews and goals.");
        }
    }
}
