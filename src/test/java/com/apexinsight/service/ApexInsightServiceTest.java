package com.apexinsight.service;

import com.apexinsight.data.Database;
import com.apexinsight.model.Employee;
import com.apexinsight.model.Evaluation;
import com.apexinsight.model.Goal;
import com.apexinsight.model.Manager;
import com.apexinsight.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApexInsightServiceTest {
    @TempDir
    Path temporaryDirectory;

    private ApexInsightService service;
    private Manager manager;
    private Employee alex;
    private Employee sam;

    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(temporaryDirectory.resolve("test.db"));
        database.initialize();
        service = new ApexInsightService(database);
        manager = assertInstanceOf(Manager.class, service.login("manager", "manager123".toCharArray()));
        List<Employee> employees = service.getEmployees(manager);
        alex = employees.stream().filter(employee -> employee.getUsername().equals("aryan")).findFirst().orElseThrow();
        sam = employees.stream().filter(employee -> employee.getUsername().equals("shikha")).findFirst().orElseThrow();
    }

    @Test
    void authenticatesEachRoleAndRejectsInvalidPassword() throws Exception {
        assertInstanceOf(Employee.class, service.login("aryan", "aryan123".toCharArray()));
        assertInstanceOf(Employee.class, service.login("shikha", "shikha123".toCharArray()));
        assertInstanceOf(Employee.class, service.login("tanvi", "tanvi123".toCharArray()));
        assertThrows(AppException.class, () -> service.login("aryan", "wrong".toCharArray()));
    }

    @Test
    void savesReviewsAndRestrictsEmployeesToTheirOwnHistory() throws Exception {
        service.addEvaluation(manager, alex.getId(), 5, "Strong delivery and thoughtful collaboration.");
        List<Evaluation> reviews = service.getEvaluations(alex, alex.getId());
        assertEquals(1, reviews.size());
        assertEquals(5, reviews.get(0).rating());
        assertEquals("Strong delivery and thoughtful collaboration.", reviews.get(0).feedback());
        assertThrows(AppException.class, () -> service.getEvaluations(alex, sam.getId()));
        assertThrows(AppException.class,
                () -> service.addEvaluation(alex, alex.getId(), 4, "Employee cannot submit a review."));
    }

    @Test
    void persistsGoalsAndAllowsManagerProgressUpdates() throws Exception {
        LocalDate dueDate = LocalDate.of(2026, 12, 31);
        service.saveGoal(manager, 0, alex.getId(), "Launch onboarding", "Publish the updated onboarding flow.",
                dueDate, "In progress", 25);
        Goal original = service.getGoals(alex).get(0);
        assertEquals(25, original.progress());
        service.saveGoal(manager, original.id(), alex.getId(), original.title(), original.description(),
                dueDate, "Completed", 100);
        Goal updated = service.getGoals(alex).get(0);
        assertEquals("Completed", updated.status());
        assertEquals(100, updated.progress());
        assertEquals(0, service.getGoals(sam).size());
    }

    @Test
    void validatesRatingsFeedbackAndGoalProgress() {
        assertThrows(AppException.class, () -> service.addEvaluation(manager, alex.getId(), 6, "Feedback."));
        assertThrows(AppException.class, () -> service.addEvaluation(manager, alex.getId(), 4, "  "));
        assertThrows(AppException.class, () -> service.saveGoal(manager, 0, alex.getId(), "Goal", "Description",
                LocalDate.now(), "In progress", 101));
        assertThrows(AppException.class, () -> service.saveGoal(manager, 0, alex.getId(), "Goal", "Description",
                LocalDate.now(), "Unknown", 0));
    }

    @Test
    void supportsEmployeeGoalProgressReviewsAndPasswordManagement() throws Exception {
        service.saveGoal(manager, 0, alex.getId(), "Ship accessible checkout",
                "Validate the new checkout against accessibility guidelines.", LocalDate.of(2027, 1, 31),
                "Not started", 0);
        Goal goal = service.getGoals(alex).get(0);
        service.updateGoalProgress(alex, goal.id(), 50);
        assertEquals(50, service.getGoals(alex).get(0).progress());
        assertEquals("In progress", service.getGoals(alex).get(0).status());
        assertThrows(AppException.class, () -> service.updateGoalProgress(sam, goal.id(), 90));

        service.addEvaluation(manager, alex.getId(), 4, "Excellent customer research.");
        Evaluation review = service.getEvaluations(alex, alex.getId()).get(0);
        service.updateEvaluation(manager, review.id(), alex.getId(), 5, "Research and outcomes were outstanding.");
        assertEquals(5, service.getEvaluations(alex, alex.getId()).get(0).rating());
        assertEquals("Research and outcomes were outstanding.",
                service.getEvaluations(alex, alex.getId()).get(0).feedback());
        service.deleteEvaluation(manager, review.id());
        service.deleteGoal(manager, goal.id());
        assertEquals(0, service.getEvaluations(alex, alex.getId()).size());
        assertEquals(0, service.getGoals(alex).size());
    }

    @Test
    void managesEmployeeAccountsAndVerifiesCurrentPassword() throws Exception {
        Employee riya = service.createEmployee(manager, "riya", "Riya Patel", "Research",
                "Research Analyst", "riyapass1".toCharArray());
        assertEquals("Research Analyst", riya.getJobTitle());
        assertInstanceOf(Employee.class, service.login("riya", "riyapass1".toCharArray()));
        assertThrows(AppException.class, () -> service.createEmployee(manager, "riya", "Duplicate",
                "Research", "Analyst", "anotherpass".toCharArray()));
        assertThrows(AppException.class, () -> service.changeOwnPassword(riya,
                "wrongpass".toCharArray(), "newpassword1".toCharArray()));
        service.changeOwnPassword(riya, "riyapass1".toCharArray(), "newpassword1".toCharArray());
        assertThrows(AppException.class, () -> service.login("riya", "riyapass1".toCharArray()));
        assertInstanceOf(Employee.class, service.login("riya", "newpassword1".toCharArray()));
        service.resetEmployeePassword(manager, riya.getId(), "resetted12".toCharArray());
        service.deleteEmployee(manager, riya.getId());
        assertThrows(AppException.class, () -> service.login("riya", "resetted12".toCharArray()));
        User aryan = alex;
        assertThrows(AppException.class, () -> service.getEmployees(aryan));
        assertThrows(AppException.class, () -> service.resetEmployeePassword(manager,
                manager.getId(), "anotherpass".toCharArray()));
    }

    @Test
    void migratesTheOriginalDemoAccountsWithoutLosingReviewHistory() throws Exception {
        service.addEvaluation(manager, alex.getId(), 4, "Carried forward from the original sample database.");
        try (var connection = new Database(temporaryDirectory.resolve("test.db")).connect();
             var rename = connection.prepareStatement("""
                     UPDATE users SET username = 'alex', full_name = 'Alex Morgan', department = 'Product',
                                      job_title = ''
                     WHERE id = ?
                     """)) {
            rename.setInt(1, alex.getId());
            rename.executeUpdate();
        }
        new Database(temporaryDirectory.resolve("test.db")).initialize();
        ApexInsightService migrated = new ApexInsightService(new Database(temporaryDirectory.resolve("test.db")));
        User newAryan = migrated.login("aryan", "aryan123".toCharArray());
        assertEquals(1, migrated.getEvaluations(newAryan, newAryan.getId()).size());
        assertEquals("Aryan", migrated.getEvaluations(newAryan, newAryan.getId()).get(0).employeeName());
        assertEquals(3, migrated.getEmployees(manager).size());
        assertThrows(AppException.class, () -> migrated.login("alex", "alex123".toCharArray()));
    }

    @Test
    void serializesConcurrentDatabaseWritesWithoutDroppingReviews() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> writes = new ArrayList<>();
            for (int number : IntStream.range(0, 16).toArray()) {
                writes.add(executor.submit(() -> {
                    service.addEvaluation(manager, alex.getId(), 3, "Concurrent review " + number);
                    return null;
                }));
            }
            for (Future<?> write : writes) write.get();
        } finally {
            executor.shutdownNow();
        }
        assertEquals(16, service.getEvaluations(alex, alex.getId()).size());
        assertEquals(16, service.getAllEvaluations(manager).size());
    }
}
