# ApexInsight

ApexInsight is a complete employee performance and development app with **two interfaces backed by the same Java service and SQLite database**:

- A role-based Java Swing desktop application.
- A responsive browser portal served locally by Java Servlets.

Managers can manage employee accounts, write and edit dated performance reviews, set measurable goals, track progress, and export reports. Employees can read their own feedback history, update their goal progress, review upcoming deadlines, export personal reports, and securely change their password.

## Start the application

Install a **JDK 17 or newer** and open a terminal in the project folder. On Windows, run:

```powershell
.\run-apexinsight.cmd
```

The launcher uses the included Maven Wrapper, so a separate Maven installation is not necessary. On first use, Maven downloads the configured Maven version and project dependencies. The desktop app opens; its sign-in screen includes **Open the web portal**. The browser portal is at <http://127.0.0.1:8085/>.

You can also start the app directly from PowerShell:

```powershell
.\mvnw.cmd exec:java
```

Build and test the project without opening the GUI:

```powershell
.\mvnw.cmd clean verify
```

### Demo sign-ins

| Account | Username | Password | Job title |
| --- | --- | --- | --- |
| Manager | `manager` | `manager123` | Manager |
| Aryan | `aryan` | `aryan123` | Product Manager |
| Shikha | `shikha` | `shikha123` | Software Engineer |
| Tanvi | `tanvi` | `tanvi123` | UX Designer |

Employees have distinct **job titles and departments**; their access role is Employee. Click an account in the browser sign-in panel to fill in its demo credentials. The manager can add, edit, and remove employees and reset employee passwords from **Team accounts**.

On first start, ApexInsight creates demo accounts with salted PBKDF2 password hashes. If you already have the earlier ApexInsight database, the startup migration preserves reviews and goals while mapping the original Alex, Sam, and Taylor demo accounts to Aryan, Shikha, and Tanvi. Their demo sign-ins then use the passwords listed above. Keep a backup of the database before using any migration with important data.

## Desktop application features

**Manager:** performance overview; search and filter review history; create, edit, and delete dated reviews; assign, search, edit, and delete goals; track a due date, status, and 0–100% progress; create and update employee profiles; reset employee passwords; and export visible report tables to CSV.

**Employee:** a private dashboard; a searchable, read-only feedback history; personal review and goal CSV exports; goal progress updates (completion automatically sets the goal to Completed); and a password-change screen that verifies the current password.

Sign-in, password hashing, database reads/writes, and file exports run in background workers so the Swing event thread remains responsive. Actions show progress and clear validation or error messages. Destructive actions require confirmation.

## Browser portal and Servlets

The embedded Jetty server uses Jakarta Servlet 5.0 APIs. It serves a responsive, accessible HTML/CSS/JavaScript interface and a JSON servlet API for sign-in, employees, reviews, goals, progress updates, password changes, and sign-out. The desktop and browser interfaces use the same validated service, role checks, DAO classes, and SQLite records.

The server deliberately **binds only to `127.0.0.1`**, not the public network. Its port defaults to `8085`; change it when starting with:

```powershell
.\mvnw.cmd "-Dapexinsight.web.port=8090" exec:java
```

Then open <http://127.0.0.1:8090/>. Sign-in creates a time-limited server-side HTTP session. The JSON API requires its application request header, rejects oversized and malformed request bodies, returns explicit authorization/validation errors, and sends no-store, content-security, and content-type headers. Do not expose this demo's HTTP server to a network or use its sample passwords for production.

## Data storage and database connectivity

The application creates `data/apexinsight.db` in the project folder. The SQLite JDBC connection enables foreign keys, WAL journaling, and a five-second busy timeout. To use a different file, set the JVM property when starting:

```powershell
.\mvnw.cmd "-Dapexinsight.db=C:\data\apexinsight.db" exec:java
```

The schema is initialized and migrated at application start:

- `users` stores a unique login, display name, `MANAGER`/`EMPLOYEE` access role, department, job title, random password salt, and PBKDF2 password hash.
- `evaluations` stores the employee and reviewer references, a rating constrained to 1–5, detailed feedback, and its creation time.
- `goals` stores an employee, description, due date, validated status, and progress constrained to 0–100.

Employee records own their reviews and goals; deleting an employee cascades to those records after a confirmation. Deleting a review or goal does not affect any other employee's records. Indexed foreign-key references, parameterized SQL statements, and database transactions keep writes consistent.

## Project design and Java concepts

| Area | Implementation |
| --- | --- |
| **OOP, inheritance, and polymorphism** | Abstract `User`, specialized `Manager` and `Employee` classes, and role-specific Swing views and authorization. |
| **Interfaces** | `Searchable` shares safe generic search across employee, review, and goal records; `ProgressTrackable` supports reusable progress summaries; `Database.WriteOperation<T>` defines transactional writes. |
| **Collections and generics** | Typed lists for employees, reviews, and goals; generic filters, DAO APIs, tables, and background tasks. |
| **Exception handling** | `AppException` with `ValidationException` and `AuthorizationException`; visible failures instead of success-shaped fallbacks. |
| **Multithreading and synchronization** | SwingWorkers and Jetty worker threads keep I/O off the GUI thread. A fair `ReentrantLock` serializes in-process SQLite writes; SQLite transactions, WAL mode, and a busy timeout support concurrent reads safely. |
| **Database operation classes** | `UserDao`, `EvaluationDao`, and `GoalDao` own prepared JDBC reads and transactional create/update/delete operations; `Database` manages connections, schema, and migration. |
| **JDBC connectivity** | Xerial SQLite JDBC is used for the local persistent database. |
| **Servlet/web integration** | Embedded Jetty Jakarta Servlets serve the browser UI and authenticated JSON API without a separate application server. |

### Source layout

```text
src/main/java/com/apexinsight/
  Main.java                    application startup
  data/                        SQLite connection management and JDBC DAOs
  model/                       users, roles, reviews, goals, shared interfaces
  service/                     validation, authorization, application workflows
  ui/                          Swing sign-in and manager/employee dashboards
  web/                         embedded Jetty servlet server and JSON API
src/main/resources/web/        browser portal, stylesheet, and client application
src/test/java/com/apexinsight/ service, database migration, concurrency, and web tests
```

## Tests

```powershell
.\mvnw.cmd test
```

JUnit 5 and a temporary SQLite file test both role sign-ins, employee privacy, manager authorization, review create/edit/delete, goal creation/editing/progress, account and password management, invalid input, sample-account migration without lost review history, concurrent database writes, servlet login and sessions, request protection, and the employee/manager JSON API.
