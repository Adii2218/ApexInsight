package com.apexinsight.data;

import com.apexinsight.model.Evaluation;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class EvaluationDao {
    private final Database database;

    public EvaluationDao(Database database) {
        this.database = database;
    }

    public void add(int employeeId, int managerId, int rating, String feedback) throws SQLException {
        database.write(connection -> {
            try (
             var statement = connection.prepareStatement("""
                     INSERT INTO evaluations(employee_id, manager_id, rating, feedback, created_at)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setInt(1, employeeId);
            statement.setInt(2, managerId);
            statement.setInt(3, rating);
            statement.setString(4, feedback);
            statement.setString(5, LocalDateTime.now().withNano(0).toString());
            statement.executeUpdate();
            }
            return null;
        });
    }

    public void update(int evaluationId, int employeeId, int rating, String feedback) throws SQLException {
        database.write(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE evaluations SET rating = ?, feedback = ?
                    WHERE id = ? AND employee_id = ?
                    """)) {
                statement.setInt(1, rating);
                statement.setString(2, feedback);
                statement.setInt(3, evaluationId);
                statement.setInt(4, employeeId);
                if (statement.executeUpdate() == 0) {
                    throw new SQLException("The selected review no longer exists.");
                }
            }
            return null;
        });
    }

    public void delete(int evaluationId) throws SQLException {
        database.write(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM evaluations WHERE id = ?")) {
                statement.setInt(1, evaluationId);
                if (statement.executeUpdate() == 0) {
                    throw new SQLException("The selected review no longer exists.");
                }
            }
            return null;
        });
    }

    public List<Evaluation> findForEmployee(int employeeId) throws SQLException {
        List<Evaluation> evaluations = new ArrayList<>();
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                     SELECT e.id, e.employee_id, u.full_name AS employee_name, m.full_name AS manager_name,
                            e.rating, e.feedback, e.created_at
                     FROM evaluations e
                     JOIN users u ON u.id = e.employee_id
                     JOIN users m ON m.id = e.manager_id
                     WHERE e.employee_id = ? ORDER BY e.created_at DESC, e.id DESC
                     """)) {
            statement.setInt(1, employeeId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    evaluations.add(new Evaluation(result.getInt("id"), result.getInt("employee_id"),
                            result.getString("employee_name"), result.getString("manager_name"),
                            result.getInt("rating"), result.getString("feedback"),
                            LocalDateTime.parse(result.getString("created_at"))));
                }
            }
        }
        return evaluations;
    }

    public List<Evaluation> findAll() throws SQLException {
        List<Evaluation> all = new ArrayList<>();
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                     SELECT e.id, e.employee_id, u.full_name AS employee_name, m.full_name AS manager_name,
                            e.rating, e.feedback, e.created_at
                     FROM evaluations e
                     JOIN users u ON u.id = e.employee_id
                     JOIN users m ON m.id = e.manager_id
                     ORDER BY e.created_at DESC, e.id DESC
                     """);
             var result = statement.executeQuery()) {
            while (result.next()) {
                all.add(new Evaluation(result.getInt("id"), result.getInt("employee_id"),
                        result.getString("employee_name"), result.getString("manager_name"),
                        result.getInt("rating"), result.getString("feedback"),
                        LocalDateTime.parse(result.getString("created_at"))));
            }
        }
        return all;
    }
}
