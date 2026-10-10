package com.apexinsight.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private static final java.util.concurrent.locks.ReentrantLock WRITE_LOCK =
            new java.util.concurrent.locks.ReentrantLock(true);
    private final Path path;

    public Database() {
        this(Path.of(System.getProperty("apexinsight.db", "data/apexinsight.db")));
    }

    public Database(Path path) {
        this.path = path.toAbsolutePath();
    }

    public Connection connect() throws SQLException {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (java.io.IOException exception) {
            throw new SQLException("Could not create the database directory", exception);
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    public <T> T write(WriteOperation<T> operation) throws SQLException {
        WRITE_LOCK.lock();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                T result = operation.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } finally {
            WRITE_LOCK.unlock();
        }
    }

    @FunctionalInterface
    public interface WriteOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    public void initialize() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        full_name TEXT NOT NULL,
                        role TEXT NOT NULL CHECK (role IN ('MANAGER', 'EMPLOYEE')),
                        department TEXT NOT NULL DEFAULT '',
                        job_title TEXT NOT NULL DEFAULT '',
                        password_salt TEXT NOT NULL,
                        password_hash TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS evaluations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        employee_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        manager_id INTEGER NOT NULL REFERENCES users(id),
                        rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
                        feedback TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS goals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        employee_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        due_date TEXT NOT NULL,
                        status TEXT NOT NULL,
                        progress INTEGER NOT NULL CHECK (progress BETWEEN 0 AND 100)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_evaluations_employee ON evaluations(employee_id, created_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_goals_employee ON goals(employee_id, due_date)");
        }
        ensureJobTitleColumn();
        seedDemoAccounts();
    }

    private void ensureJobTitleColumn() throws SQLException {
        boolean found = false;
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             var columns = statement.executeQuery("PRAGMA table_info(users)")) {
            while (columns.next()) {
                if ("job_title".equals(columns.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            try (Connection connection = connect(); Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE users ADD COLUMN job_title TEXT NOT NULL DEFAULT ''");
            }
        }
    }

    private void seedDemoAccounts() throws SQLException {
        write(connection -> {
            ensureDemoUser(connection, "manager", "Jordan Lee", "MANAGER", "", "Manager", "manager123");
            ensureDemoUser(connection, "aryan", "Aryan", "EMPLOYEE", "Product", "Product Manager", "aryan123");
            ensureDemoUser(connection, "shikha", "Shikha", "EMPLOYEE", "Engineering", "Software Engineer", "shikha123");
            ensureDemoUser(connection, "tanvi", "Tanvi", "EMPLOYEE", "Design", "UX Designer", "tanvi123");
            migrateLegacyDemoUser(connection, "alex", "aryan", "Aryan", "Product", "Product Manager", "aryan123");
            migrateLegacyDemoUser(connection, "sam", "shikha", "Shikha", "Engineering", "Software Engineer", "shikha123");
            migrateLegacyDemoUser(connection, "taylor", "tanvi", "Tanvi", "Design", "UX Designer", "tanvi123");
            return null;
        });
    }

    private static void ensureDemoUser(Connection connection, String username, String name, String role,
                                       String department, String jobTitle, String password) throws SQLException {
        try (var check = connection.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            check.setString(1, username);
            try (var results = check.executeQuery()) {
                if (results.next()) {
                    try (var update = connection.prepareStatement("""
                            UPDATE users SET full_name = ?, department = ?, job_title = ?
                            WHERE username = ? AND role = ?
                            """)) {
                        update.setString(1, name);
                        update.setString(2, department);
                        update.setString(3, jobTitle);
                        update.setString(4, username);
                        update.setString(5, role);
                        if (update.executeUpdate() != 1) {
                            throw new SQLException("The reserved demo username " + username
                                    + " belongs to a different account role.");
                        }
                    }
                    return;
                }
            }
        }
        insertDemoUser(connection, username, name, role, department, jobTitle, password);
    }

    private static void migrateLegacyDemoUser(Connection connection, String oldUsername, String username,
                                              String name, String department, String jobTitle, String password)
            throws SQLException {
        try (var find = connection.prepareStatement(
                "SELECT id FROM users WHERE username = ? AND role = 'EMPLOYEE'")) {
            find.setString(1, oldUsername);
            try (var result = find.executeQuery()) {
                if (!result.next()) return;
                int id = result.getInt("id");
                try (var check = connection.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
                    check.setString(1, username);
                    try (var target = check.executeQuery()) {
                        if (target.next()) {
                            int newEmployeeId;
                            try (var lookup = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                                lookup.setString(1, username);
                                try (var row = lookup.executeQuery()) {
                                    row.next();
                                    newEmployeeId = row.getInt(1);
                                }
                            }
                            try (var updateReviews = connection.prepareStatement(
                                    "UPDATE evaluations SET employee_id = ? WHERE employee_id = ?");
                                 var updateGoals = connection.prepareStatement(
                                         "UPDATE goals SET employee_id = ? WHERE employee_id = ?");
                                 var delete = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
                                updateReviews.setInt(1, newEmployeeId);
                                updateReviews.setInt(2, id);
                                updateReviews.executeUpdate();
                                updateGoals.setInt(1, newEmployeeId);
                                updateGoals.setInt(2, id);
                                updateGoals.executeUpdate();
                                delete.setInt(1, id);
                                delete.executeUpdate();
                            }
                            return;
                        }
                    }
                }
                String salt = PasswordHasher.newSalt();
                try (var update = connection.prepareStatement("""
                        UPDATE users SET username = ?, full_name = ?, department = ?, job_title = ?,
                                         password_salt = ?, password_hash = ? WHERE id = ?
                        """)) {
                    update.setString(1, username);
                    update.setString(2, name);
                    update.setString(3, department);
                    update.setString(4, jobTitle);
                    update.setString(5, salt);
                    update.setString(6, PasswordHasher.hash(password.toCharArray(), salt));
                    update.setInt(7, id);
                    update.executeUpdate();
                }
            }
        }
    }

    private static void insertDemoUser(Connection connection, String username, String name, String role,
                                       String department, String jobTitle, String password) throws SQLException {
        String salt = PasswordHasher.newSalt();
        try (var insert = connection.prepareStatement("""
                INSERT INTO users(username, full_name, role, department, job_title, password_salt, password_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, username);
            insert.setString(2, name);
            insert.setString(3, role);
            insert.setString(4, department);
            insert.setString(5, jobTitle);
            insert.setString(6, salt);
            insert.setString(7, PasswordHasher.hash(password.toCharArray(), salt));
            insert.executeUpdate();
        }
    }
}
