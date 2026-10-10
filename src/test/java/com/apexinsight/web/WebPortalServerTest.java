package com.apexinsight.web;

import com.apexinsight.data.Database;
import com.apexinsight.service.ApexInsightService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPortalServerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    private WebPortalServer server;
    private String baseUrl;
    private HttpClient manager;
    private HttpClient aryan;

    @BeforeEach
    void setUp() throws Exception {
        Database database = new Database(temporaryDirectory.resolve("web.db"));
        database.initialize();
        server = new WebPortalServer(new ApexInsightService(database), 0);
        server.start();
        baseUrl = server.getUrl();
        manager = newClient();
        aryan = newClient();
    }

    @AfterEach
    void shutDown() throws Exception {
        if (server != null) server.close();
    }

    @Test
    void servesPortalAndProtectsAuthenticatedServletEndpoints() throws Exception {
        HttpResponse<String> page = send(manager, "GET", "/", null, true);
        assertEquals(200, page.statusCode(), page.body());
        assertTrue(page.body().contains("ApexInsight"));
        assertEquals(200, send(manager, "GET", "/assets/app.js", null, true).statusCode());
        assertEquals(401, send(manager, "GET", "/api/me", null, true).statusCode());
        assertEquals(403, send(manager, "POST", "/api/login", "{}", false).statusCode());

        HttpResponse<String> managerSignIn = login(manager, "manager", "manager123");
        assertEquals(200, managerSignIn.statusCode());
        String sessionCookie = managerSignIn.headers().firstValue("set-cookie").orElseThrow();
        assertTrue(sessionCookie.contains("HttpOnly"));
        assertTrue(sessionCookie.contains("SameSite=Strict"));
        JsonNode employees = JSON.readTree(send(manager, "GET", "/api/employees", null, true).body());
        int aryanId = employeeId(employees, "aryan");
        int shikhaId = employeeId(employees, "shikha");
        assertTrue(aryanId > 0);

        assertEquals(201, send(manager, "POST", "/api/reviews",
                """
                {"employeeId":%d,"rating":5,"feedback":"Thoughtful and excellent project delivery."}
                """.formatted(aryanId), true).statusCode());
        JsonNode managerReviews = JSON.readTree(send(manager, "GET", "/api/reviews", null, true).body());
        assertEquals(1, managerReviews.size());
        int reviewId = managerReviews.get(0).get("id").intValue();
        assertEquals(400, send(manager, "POST", "/api/reviews", "{ invalid", true).statusCode());

        assertEquals(201, send(manager, "POST", "/api/goals",
                """
                {"employeeId":%d,"title":"Deliver launch","description":"Ship the launch plan.",
                 "dueDate":"2027-06-30","status":"In progress","progress":20}
                """.formatted(aryanId), true).statusCode());
        JsonNode assignedGoals = JSON.readTree(send(manager, "GET", "/api/goals", null, true).body());
        assertEquals(1, assignedGoals.size());
        int goalId = assignedGoals.get(0).get("id").intValue();

        assertEquals(200, login(aryan, "aryan", "aryan123").statusCode());
        assertEquals(1, JSON.readTree(send(aryan, "GET", "/api/reviews", null, true).body()).size());
        assertEquals(403, send(aryan, "GET", "/api/employees", null, true).statusCode());
        JsonNode tamperedReviews = JSON.readTree(send(aryan, "GET", "/api/reviews?employeeId="
                + shikhaId, null, true).body());
        for (JsonNode review : tamperedReviews) {
            assertEquals(aryanId, review.get("employeeId").intValue());
        }
        assertEquals(403, send(aryan, "POST", "/api/reviews", "{}", true).statusCode());

        assertEquals(200, send(aryan, "PUT", "/api/goals/progress",
                """
                {"id":%d,"progress":75}
                """.formatted(goalId), true).statusCode());
        JsonNode employeeGoals = JSON.readTree(send(aryan, "GET", "/api/goals", null, true).body());
        assertEquals(75, employeeGoals.get(0).get("progress").intValue());
        assertEquals(200, send(manager, "DELETE", "/api/reviews?id=" + reviewId, null, true).statusCode());
        assertEquals(200, send(manager, "DELETE", "/api/goals?id=" + goalId, null, true).statusCode());
    }

    private HttpResponse<String> login(HttpClient client, String username, String password) throws Exception {
        return send(client, "POST", "/api/login", JSON.writeValueAsString(
                java.util.Map.of("username", username, "password", password)), true);
    }

    private int employeeId(JsonNode employees, String username) {
        for (JsonNode employee : employees) {
            if (employee.get("username").asText().equals(username)) return employee.get("id").intValue();
        }
        throw new AssertionError("Demo employee account was not returned by the employee servlet.");
    }

    private HttpResponse<String> send(HttpClient client, String method, String path, String body,
                                      boolean includeCsrfHeader) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl
                        + (path.equals("/") ? "" : path.startsWith("/") ? path.substring(1) : path)))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json,text/html");
        if (includeCsrfHeader) request.header("X-ApexInsight-Request", "1");
        if (body != null) request.header("Content-Type", "application/json");
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        request.method(method, publisher);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpClient newClient() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
    }
}
