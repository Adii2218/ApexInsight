package com.apexinsight.data;

import com.apexinsight.model.Employee;
import com.apexinsight.model.Manager;
import com.apexinsight.model.Role;
import com.apexinsight.model.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UserDao {
    private final Database database;

    public UserDao(Database database) {
        this.database = database;
    }

    public Optional<User> authenticate(String username, char[] password) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                     SELECT id, username, full_name, role, department, job_title, password_salt, password_hash
                     FROM users WHERE username = ?
                     """)) {
            statement.setString(1, username);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !PasswordHasher.matches(password, result.getString("password_salt"),
                        result.getString("password_hash"))) {
                    return Optional.empty();
                }
                int id = result.getInt("id");
                String login = result.getString("username");
                String name = result.getString("full_name");
                Role role = Role.valueOf(result.getString("role"));
                return Optional.of(role == Role.MANAGER
                        ? new Manager(id, login, name)
                        : new Employee(id, login, name, result.getString("department"),
                        result.getString("job_title")));
            }
        }
    }

    public List<Employee> findEmployees() throws SQLException {
        List<Employee> employees = new ArrayList<>();
        try (var connection = database.connect();
             var statement = connection.prepareStatement("""
                     SELECT id, username, full_name, department, job_title FROM users
                     WHERE role = 'EMPLOYEE' ORDER BY full_name
                     """);
             var result = statement.executeQuery()) {
            while (result.next()) {
                employees.add(new Employee(result.getInt("id"), result.getString("username"),
                        result.getString("full_name"), result.getString("department"),
                        result.getString("job_title")));
            }
        }
        return employees;
    }

    public boolean isEmployee(int id) throws SQLException {
        try (var connection = database.connect();
             var statement = connection.prepareStatement(
                     "SELECT 1 FROM users WHERE id = ? AND role = 'EMPLOYEE'")) {
            statement.setInt(1, id);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public Employee createEmployee(String username, String fullName, String department, String jobTitle,
                                   char[] password) throws SQLException {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash(password, salt);
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO users(username, full_name, role, department, job_title, password_salt, password_hash)
                    VALUES (?, ?, 'EMPLOYEE', ?, ?, ?, ?)
                    """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, username);
                statement.setString(2, fullName);
                statement.setString(3, department);
                statement.setString(4, jobTitle);
                statement.setString(5, salt);
                statement.setString(6, hash);
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return new Employee(keys.getInt(1), username, fullName, department, jobTitle);
                    }
                    throw new SQLException("The employee could not be created.");
                }
            }
        });
    }

    public Employee updateEmployee(int id, String username, String fullName, String department, String jobTitle)
            throws SQLException {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE users SET username = ?, full_name = ?, department = ?, job_title = ?
                    WHERE id = ? AND role = 'EMPLOYEE'
                    """)) {
                statement.setString(1, username);
                statement.setString(2, fullName);
                statement.setString(3, department);
                statement.setString(4, jobTitle);
                statement.setInt(5, id);
                if (statement.executeUpdate() == 0) {
                    throw new SQLException("The selected employee no longer exists.");
                }
            }
            return new Employee(id, username, fullName, department, jobTitle);
        });
    }

    public boolean changePassword(int id, char[] password) throws SQLException {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash(password, salt);
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE users SET password_salt = ?, password_hash = ? WHERE id = ?
                    """)) {
                statement.setString(1, salt);
                statement.setString(2, hash);
                statement.setInt(3, id);
                return statement.executeUpdate() > 0;
            }
        });
    }

    public boolean deleteEmployee(int id) throws SQLException {
        return database.write(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM users WHERE id = ? AND role = 'EMPLOYEE'")) {
                statement.setInt(1, id);
                return statement.executeUpdate() > 0;
            }
        });
    }
}
