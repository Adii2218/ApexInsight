package com.apexinsight.data;

import com.apexinsight.model.Goal;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class GoalDao {
    private final Database database;

    public GoalDao(Database database) {
        this.database = database;
    }

    public Goal save(Goal goal) throws SQLException {
        return database.write(connection -> {
            if (goal.id() > 0) {
                try (var statement = connection.prepareStatement("""
                        UPDATE goals SET employee_id = ?, title = ?, description = ?, due_date = ?,
                                         status = ?, progress = ? WHERE id = ?
                        """)) {
                    bind(statement, goal);
                    statement.setInt(7, goal.id());
                    if (statement.executeUpdate() == 0) {
                        throw new SQLException("The selected goal no longer exists.");
                    }
                }
                return goal;
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO goals(employee_id, title, description, due_date, status, progress)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, goal);
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new Goal(keys.getInt(1), goal.employeeId(), goal.employeeName(), goal.title(),
                                goal.description(), goal.dueDate(), goal.status(), goal.progress());
                    }
                }
            }
            throw new SQLException("The new goal could not be saved.");
        });
    }

    private static void bind(java.sql.PreparedStatement statement, Goal goal) throws SQLException {
        statement.setInt(1, goal.employeeId());
        statement.setString(2, goal.title());
        statement.setString(3, goal.description());
        statement.setString(4, goal.dueDate().toString());
        statement.setString(5, goal.status());
        statement.setInt(6, goal.progress());
    }

    public void delete(int goalId) throws SQLException {
        database.write(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM goals WHERE id = ?")) {
                statement.setInt(1, goalId);
                if (statement.executeUpdate() == 0) {
                    throw new SQLException("The selected goal no longer exists.");
                }
            }
            return null;
        });
    }

    public boolean updateEmployeeProgress(int goalId, int employeeId, int progress, String status)
            throws SQLException {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE goals SET progress = ?, status = ? WHERE id = ? AND employee_id = ?
                    """)) {
                statement.setInt(1, progress);
                statement.setString(2, status);
                statement.setInt(3, goalId);
                statement.setInt(4, employeeId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    public Goal findById(int goalId) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                     SELECT g.id, g.employee_id, u.full_name AS employee_name, g.title, g.description,
                            g.due_date, g.status, g.progress
                     FROM goals g JOIN users u ON u.id = g.employee_id WHERE g.id = ?
                     """)) {
            statement.setInt(1, goalId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new Goal(result.getInt("id"), result.getInt("employee_id"),
                        result.getString("employee_name"), result.getString("title"),
                        result.getString("description"), LocalDate.parse(result.getString("due_date")),
                        result.getString("status"), result.getInt("progress"));
            }
        }
    }

    public List<Goal> findForEmployee(int employeeId) throws SQLException {
        List<Goal> goals = new ArrayList<>();
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                     SELECT g.id, g.employee_id, u.full_name AS employee_name, g.title, g.description,
                            g.due_date, g.status, g.progress
                     FROM goals g JOIN users u ON u.id = g.employee_id
                     WHERE g.employee_id = ? ORDER BY g.due_date, g.id
                     """)) {
            statement.setInt(1, employeeId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    goals.add(new Goal(result.getInt("id"), result.getInt("employee_id"),
                            result.getString("employee_name"), result.getString("title"),
                            result.getString("description"), LocalDate.parse(result.getString("due_date")),
                            result.getString("status"), result.getInt("progress")));
                }
            }
        }
        return goals;
    }

    public List<Goal> findAll() throws SQLException {
        List<Goal> goals = new ArrayList<>();
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                     SELECT g.id, g.employee_id, u.full_name AS employee_name, g.title, g.description,
                            g.due_date, g.status, g.progress
                     FROM goals g JOIN users u ON u.id = g.employee_id
                     ORDER BY u.full_name, g.due_date, g.id
                     """);
             var result = statement.executeQuery()) {
            while (result.next()) {
                goals.add(new Goal(result.getInt("id"), result.getInt("employee_id"),
                        result.getString("employee_name"), result.getString("title"),
                        result.getString("description"), LocalDate.parse(result.getString("due_date")),
                        result.getString("status"), result.getInt("progress")));
            }
        }
        return goals;
    }
}
