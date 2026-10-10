package com.apexinsight.web;

import com.apexinsight.model.Employee;
import com.apexinsight.model.Evaluation;
import com.apexinsight.model.Goal;
import com.apexinsight.model.Manager;
import com.apexinsight.model.Searchable;
import com.apexinsight.model.User;
import com.apexinsight.service.AppException;
import com.apexinsight.service.ApexInsightService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WebPortalServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(WebPortalServer.class.getName());
    private static final int MAX_JSON_BYTES = 1_048_576;
    private final Server server;
    private final ServerConnector connector;
    private final int requestedPort;

    public WebPortalServer(ApexInsightService service) {
        this(service, Integer.getInteger("apexinsight.web.port", 8085));
    }

    public WebPortalServer(ApexInsightService service, int port) {
        requestedPort = port;
        server = new Server();
        connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(port);
        server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.getSessionHandler().setMaxInactiveInterval(30 * 60);
        context.getSessionHandler().setHttpOnly(true);
        context.getSessionHandler().setSameSite(HttpCookie.SameSite.STRICT);
        context.addServlet(new ServletHolder(new PortalServlet()), "/");
        context.addServlet(new ServletHolder(new AssetServlet()), "/assets/*");
        context.addServlet(new ServletHolder(new ApiServlet(service)), "/api/*");
        server.setHandler(context);
        server.setStopAtShutdown(false);
    }

    public synchronized void start() throws Exception {
        server.start();
    }

    public int getPort() {
        return connector.getLocalPort() == 0 ? requestedPort : connector.getLocalPort();
    }

    public String getUrl() {
        return "http://127.0.0.1:" + getPort() + "/";
    }

    @Override
    public synchronized void close() throws Exception {
        if (server.isRunning() || server.isStarting()) server.stop();
    }

    private static final class PortalServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            serveResource(request, response, "/web/index.html");
        }
    }

    private static final class AssetServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String file = request.getPathInfo();
            if (file == null || !file.matches("/[A-Za-z0-9._-]+")) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            serveResource(request, response, "/web" + file);
        }
    }

    private static void serveResource(HttpServletRequest request, HttpServletResponse response, String name)
            throws IOException {
        String type = name.endsWith(".js") ? "application/javascript; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8";
        response.setContentType(type);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'");
        try (InputStream source = WebPortalServer.class.getResourceAsStream(name)) {
            if (source == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            source.transferTo(response.getOutputStream());
        }
    }

    private static final class ApiServlet extends HttpServlet {
        private final ApexInsightService service;
        private final ObjectMapper mapper = new ObjectMapper();

        private ApiServlet(ApexInsightService service) {
            this.service = service;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            try {
                String path = request.getPathInfo();
                User user = requireUser(request, response, false);
                if (user == null) return;
                if ("/me".equals(path)) {
                    sendJson(response, 200, userJson(user));
                } else if ("/employees".equals(path)) {
                    requireManager(user);
                    sendJson(response, 200, Searchable.matching(service.getEmployees(user),
                            request.getParameter("search")).stream().map(ApiServlet::employeeJson).toList());
                } else if ("/reviews".equals(path)) {
                    if (user instanceof Manager) {
                        String employeeId = request.getParameter("employeeId");
                        List<Evaluation> reviews = employeeId == null || employeeId.isBlank()
                                ? service.getAllEvaluations(user)
                                : service.getEvaluations(user, Integer.parseInt(employeeId));
                        sendJson(response, 200, Searchable.matching(reviews,
                                request.getParameter("search")).stream().map(ApiServlet::evaluationJson).toList());
                    } else {
                        sendJson(response, 200,
                                Searchable.matching(service.getEvaluations(user, user.getId()),
                                        request.getParameter("search")).stream().map(ApiServlet::evaluationJson).toList());
                    }
                } else if ("/goals".equals(path)) {
                    sendJson(response, 200, Searchable.matching(service.getGoals(user),
                            request.getParameter("search")).stream().map(ApiServlet::goalJson).toList());
                } else {
                    sendJson(response, 404, error("Endpoint not found."));
                }
            } catch (Exception exception) {
                handleFailure(response, exception);
            }
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            String path = request.getPathInfo();
            if (!requireAjax(request, response)) return;
            if ("/login".equals(path)) {
                login(request, response);
                return;
            }
            if ("/logout".equals(path)) {
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                sendJson(response, 200, Map.of("ok", true));
                return;
            }
            try {
                User user = requireUser(request, response, false);
                if (user == null) return;
                JsonNode body = readBody(request, response);
                if (body == null) return;
                if ("/reviews".equals(path)) {
                    requireManager(user);
                    service.addEvaluation(user, requiredInt(body, "employeeId"), requiredInt(body, "rating"),
                            requiredText(body, "feedback"));
                    sendJson(response, 201, Map.of("ok", true));
                } else if ("/goals".equals(path)) {
                    requireManager(user);
                    service.saveGoal(user, 0, requiredInt(body, "employeeId"), requiredText(body, "title"),
                            requiredText(body, "description"), LocalDate.parse(requiredText(body, "dueDate")),
                            requiredText(body, "status"), requiredInt(body, "progress"));
                    sendJson(response, 201, Map.of("ok", true));
                } else if ("/employees".equals(path)) {
                    requireManager(user);
                    char[] password = requiredText(body, "password").toCharArray();
                    try {
                        Employee employee = service.createEmployee(user, requiredText(body, "username"),
                                requiredText(body, "fullName"), requiredText(body, "department"),
                                requiredText(body, "jobTitle"), password);
                        sendJson(response, 201, employeeJson(employee));
                    } finally {
                        java.util.Arrays.fill(password, '\0');
                    }
                } else if ("/password".equals(path)) {
                    char[] current = requiredText(body, "currentPassword").toCharArray();
                    char[] next = requiredText(body, "newPassword").toCharArray();
                    try {
                        service.changeOwnPassword(user, current, next);
                        sendJson(response, 200, Map.of("ok", true));
                    } finally {
                        java.util.Arrays.fill(current, '\0');
                        java.util.Arrays.fill(next, '\0');
                    }
                } else {
                    sendJson(response, 404, error("Endpoint not found."));
                }
            } catch (Exception exception) {
                handleFailure(response, exception);
            }
        }

        @Override
        protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (!requireAjax(request, response)) return;
            try {
                User user = requireUser(request, response, false);
                if (user == null) return;
                JsonNode body = readBody(request, response);
                if (body == null) return;
                String path = request.getPathInfo();
                if ("/reviews".equals(path)) {
                    requireManager(user);
                    service.updateEvaluation(user, requiredInt(body, "id"), requiredInt(body, "employeeId"),
                            requiredInt(body, "rating"), requiredText(body, "feedback"));
                    sendJson(response, 200, Map.of("ok", true));
                } else if ("/goals".equals(path)) {
                    requireManager(user);
                    service.saveGoal(user, requiredInt(body, "id"), requiredInt(body, "employeeId"),
                            requiredText(body, "title"), requiredText(body, "description"),
                            LocalDate.parse(requiredText(body, "dueDate")), requiredText(body, "status"),
                            requiredInt(body, "progress"));
                    sendJson(response, 200, Map.of("ok", true));
                } else if ("/goals/progress".equals(path)) {
                    service.updateGoalProgress(user, requiredInt(body, "id"), requiredInt(body, "progress"));
                    sendJson(response, 200, Map.of("ok", true));
                } else if ("/employees".equals(path)) {
                    requireManager(user);
                    Employee employee = service.updateEmployee(user, requiredInt(body, "id"),
                            requiredText(body, "username"), requiredText(body, "fullName"),
                            requiredText(body, "department"), requiredText(body, "jobTitle"));
                    sendJson(response, 200, employeeJson(employee));
                } else if ("/employees/password".equals(path)) {
                    requireManager(user);
                    char[] password = requiredText(body, "password").toCharArray();
                    try {
                        service.resetEmployeePassword(user, requiredInt(body, "id"), password);
                        sendJson(response, 200, Map.of("ok", true));
                    } finally {
                        java.util.Arrays.fill(password, '\0');
                    }
                } else {
                    sendJson(response, 404, error("Endpoint not found."));
                }
            } catch (Exception exception) {
                handleFailure(response, exception);
            }
        }

        @Override
        protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (!requireAjax(request, response)) return;
            try {
                User user = requireUser(request, response, false);
                if (user == null) return;
                requireManager(user);
                String path = request.getPathInfo();
                int id = Integer.parseInt(requiredQuery(request, "id"));
                if ("/reviews".equals(path)) service.deleteEvaluation(user, id);
                else if ("/goals".equals(path)) service.deleteGoal(user, id);
                else if ("/employees".equals(path)) service.deleteEmployee(user, id);
                else {
                    sendJson(response, 404, error("Endpoint not found."));
                    return;
                }
                sendJson(response, 200, Map.of("ok", true));
            } catch (Exception exception) {
                handleFailure(response, exception);
            }
        }

        private void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
            char[] password = new char[0];
            try {
                JsonNode body = readBody(request, response);
                if (body == null) return;
                password = requiredText(body, "password").toCharArray();
                User user = service.login(requiredText(body, "username"), password);
                HttpSession existing = request.getSession(false);
                if (existing != null) existing.invalidate();
                HttpSession session = request.getSession(true);
                session.setMaxInactiveInterval(30 * 60);
                session.setAttribute("apexinsight.user", user);
                sendJson(response, 200, userJson(user));
            } catch (Exception exception) {
                handleFailure(response, exception);
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
        }

        private User requireUser(HttpServletRequest request, HttpServletResponse response, boolean managerOnly)
                throws IOException {
            HttpSession session = request.getSession(false);
            Object value = session == null ? null : session.getAttribute("apexinsight.user");
            if (!(value instanceof User user)) {
                sendJson(response, 401, error("Please sign in again."));
                return null;
            }
            if (managerOnly && !(user instanceof Manager)) {
                sendJson(response, 403, error("Only managers can do that."));
                return null;
            }
            return user;
        }

        private boolean requireAjax(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (!"1".equals(request.getHeader("X-ApexInsight-Request"))) {
                sendJson(response, 403, error("This request is not allowed."));
                return false;
            }
            return true;
        }

        private JsonNode readBody(HttpServletRequest request, HttpServletResponse response) throws IOException {
            byte[] body = request.getInputStream().readNBytes(MAX_JSON_BYTES + 1);
            if (body.length > MAX_JSON_BYTES) {
                sendJson(response, 413, error("Request is too large."));
                return null;
            }
            JsonNode json = mapper.readTree(body);
            if (json == null || !json.isObject()) {
                sendJson(response, 400, error("Send a JSON object."));
                return null;
            }
            return json;
        }

        private static int requiredInt(JsonNode body, String field) {
            JsonNode value = body.get(field);
            if (value == null || !value.canConvertToInt()) throw new IllegalArgumentException(field + " must be a number.");
            return value.intValue();
        }

        private static String requiredText(JsonNode body, String field) {
            JsonNode value = body.get(field);
            if (value == null || !value.isTextual()) throw new IllegalArgumentException(field + " is required.");
            return value.textValue();
        }

        private static String requiredQuery(HttpServletRequest request, String field) {
            String value = request.getParameter(field);
            if (value == null) throw new IllegalArgumentException(field + " is required.");
            return value;
        }

        private static void requireManager(User user) {
            if (!(user instanceof Manager)) throw new ForbiddenOperationException();
        }

        private static Map<String, Object> userJson(User user) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", user.getId());
            result.put("username", user.getUsername());
            result.put("fullName", user.getFullName());
            result.put("role", user.getRole().name());
            if (user instanceof Employee employee) {
                result.put("department", employee.getDepartment());
                result.put("jobTitle", employee.getJobTitle());
            }
            return result;
        }

        private static Map<String, Object> employeeJson(Employee employee) {
            return userJson(employee);
        }

        private static Map<String, Object> evaluationJson(Evaluation review) {
            return Map.of("id", review.id(), "employeeId", review.employeeId(), "employeeName",
                    review.employeeName(), "managerName", review.managerName(), "rating", review.rating(),
                    "feedback", review.feedback(), "createdAt", review.createdAt().toString());
        }

        private static Map<String, Object> goalJson(Goal goal) {
            return Map.of("id", goal.id(), "employeeId", goal.employeeId(), "employeeName",
                    goal.employeeName(), "title", goal.title(), "description", goal.description(),
                    "dueDate", goal.dueDate().toString(), "status", goal.status(), "progress", goal.progress());
        }

        private static Map<String, Object> error(String message) {
            return Map.of("error", message);
        }

        private void handleFailure(HttpServletResponse response, Exception exception) throws IOException {
            if (exception instanceof ForbiddenOperationException) {
                sendJson(response, 403, error(exception.getMessage()));
                return;
            }
            if (exception instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                sendJson(response, 400, error("The request body is not valid JSON."));
                return;
            }
            if (exception instanceof AppException applicationError) {
                int status = applicationError.getMessage().startsWith("Only managers")
                        || applicationError.getMessage().contains("only view your own")
                        || applicationError.getMessage().contains("only update your own")
                        ? 403 : applicationError.getMessage().contains("username or password") ? 401 : 400;
                sendJson(response, status, error(applicationError.getMessage()));
                return;
            }
            if (exception instanceof IllegalArgumentException || exception instanceof java.time.DateTimeException) {
                sendJson(response, 400, error(exception.getMessage() == null ? "Check your input." : exception.getMessage()));
                return;
            }
            LOGGER.log(Level.SEVERE, "Web portal request failed", exception);
            sendJson(response, 500, error("The request could not be completed."));
        }

        private void sendJson(HttpServletResponse response, int status, Object value) throws IOException {
            response.setStatus(status);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "same-origin");
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'");
            mapper.writeValue(response.getOutputStream(), value);
        }
    }

    private static final class ForbiddenOperationException extends RuntimeException {
        private ForbiddenOperationException() {
            super("Only managers can do that.");
        }
    }
}
