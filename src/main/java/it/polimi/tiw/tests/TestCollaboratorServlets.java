package it.polimi.tiw.tests;

import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.stream.Collectors;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.*;

public class TestCollaboratorServlets {

    // === ADAPTA ===
    static final String BASE    = "http://localhost:8080/TIW-Project";
    static final String DB_URL  = "jdbc:mysql://localhost:3306/TIW_DB?serverTimezone=UTC";
    static final String DB_USER = "luis";
    static final String DB_PASS = "Password@2026";

    static final String PREFIX = "TEST_CS_" + System.currentTimeMillis() + "_";

    static HttpClient client;
    static Connection db;
    static int ok = 0, fail = 0;

    static int adminId, managerId, collaboratorId, otherTecnicoId;
    static int assignedProjId, createdProjId;
    static int assignedWpId, createdWpId;
    static int assignedTaskId;
    static int unassignedTaskId;
    static int createdProjTaskId;

    // ---------- helpers ----------

    static void check(String label, boolean cond) {
        if (cond) { ok++; System.out.println("OK   " + label); }
        else      { fail++; System.out.println("FAIL " + label); }
    }

    static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }

    static Map<String, String> form(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static HttpResponse<String> post(String path, Map<String, String> params) throws Exception {
        String body = params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> r = client.send(req, BodyHandlers.ofString());
        System.out.println("   POST " + path + " -> status=" + r.statusCode()
                + " location=" + location(r));
        if (r.statusCode() >= 400) {
            String body1 = r.body();
            System.out.println("   body: " + (body1.length() > 300 ? body1.substring(0, 300) + "..." : body1));
        }
        return r;
    }

    static HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build();
        HttpResponse<String> r = client.send(req, BodyHandlers.ofString());
        System.out.println("   GET  " + path + " -> status=" + r.statusCode()
                + " location=" + location(r));
        return r;
    }

    static String location(HttpResponse<?> r) {
        return r.headers().firstValue("Location").orElse("");
    }

    // ---------- setup ----------

    static void setup() throws Exception {
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder()
                .cookieHandler(cm)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        Class.forName("com.mysql.cj.jdbc.Driver");
        db = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);

        UserDAO userDAO = new UserDAO(db);
        ProjectDAO projectDAO = new ProjectDAO(db);
        WorkPackageDAO wpDAO = new WorkPackageDAO(db);
        TaskDAO taskDAO = new TaskDAO(db);

        User admin = userDAO.checkCredentials("admin1", "admin1");
        if (admin == null) throw new RuntimeException("admin1 no existe");
        adminId = admin.getId();

        List<User> tecnicos = userDAO.findAllTechnicals();
        if (tecnicos.size() < 3) throw new RuntimeException("se necesitan >=3 tecnicos");
        managerId       = tecnicos.get(0).getId();
        collaboratorId  = tecnicos.get(1).getId();
        otherTecnicoId  = tecnicos.get(2).getId();

        // Proyecto ASSIGNED con dos tasks (uno nuestro, uno de otro tecnico)
        assignedProjId = projectDAO.createProject(PREFIX + "ProjA", 12, adminId, managerId);
        assignedWpId   = wpDAO.createWorkPackage(assignedProjId, PREFIX + "WP", 1, 6);

        assignedTaskId   = taskDAO.createTask(assignedWpId, PREFIX + "Tassigned", "desc", 1, 4);
        unassignedTaskId = taskDAO.createTask(assignedWpId, PREFIX + "Tother",    "desc", 1, 4);

        insertAssignment(assignedTaskId,   collaboratorId);
        insertAssignment(unassignedTaskId, otherTecnicoId);

        insertPlannedHours(assignedTaskId,   1, 4, 10);
        insertPlannedHours(unassignedTaskId, 1, 4, 10);

        projectDAO.updateState(assignedProjId, "ASSIGNED");

        // Proyecto CREATED para probar rechazo por estado
        createdProjId = projectDAO.createProject(PREFIX + "ProjC", 6, adminId, managerId);
        createdWpId   = wpDAO.createWorkPackage(createdProjId, PREFIX + "WP", 1, 3);
        createdProjTaskId = taskDAO.createTask(createdWpId, PREFIX + "TinCREATED", "desc", 1, 3);
        insertAssignment(createdProjTaskId, collaboratorId);

        System.out.println("Fixtures: assignedProjId=" + assignedProjId
                + " assignedTaskId=" + assignedTaskId
                + " unassignedTaskId=" + unassignedTaskId
                + " createdProjTaskId=" + createdProjTaskId
                + " collaboratorId=" + collaboratorId);
    }

    static void insertAssignment(int taskId, int collabId) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO task_assignments (task_id, collaborator_id) VALUES (?, ?)")) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            ps.executeUpdate();
        }
    }

    static void insertPlannedHours(int taskId, int monthFrom, int monthTo, int hours) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO planned_hours (task_id, month_index, hours) VALUES (?, ?, ?)")) {
            for (int m = monthFrom; m <= monthTo; m++) {
                ps.setInt(1, taskId);
                ps.setInt(2, m);
                ps.setInt(3, hours);
                ps.executeUpdate();
            }
        }
    }

    static void teardown() {
        if (db == null) return;
        runSilent("DELETE wh FROM worked_hours wh JOIN tasks t ON wh.task_id = t.id "
                + "JOIN work_packages w ON t.wp_id = w.id JOIN projects p ON w.project_id = p.id "
                + "WHERE p.title LIKE '" + PREFIX + "%'");
        runSilent("DELETE ph FROM planned_hours ph JOIN tasks t ON ph.task_id = t.id "
                + "JOIN work_packages w ON t.wp_id = w.id JOIN projects p ON w.project_id = p.id "
                + "WHERE p.title LIKE '" + PREFIX + "%'");
        runSilent("DELETE a FROM task_assignments a JOIN tasks t ON a.task_id = t.id "
                + "JOIN work_packages w ON t.wp_id = w.id JOIN projects p ON w.project_id = p.id "
                + "WHERE p.title LIKE '" + PREFIX + "%'");
        runSilent("DELETE t FROM tasks t JOIN work_packages w ON t.wp_id = w.id "
                + "JOIN projects p ON w.project_id = p.id WHERE p.title LIKE '" + PREFIX + "%'");
        runSilent("DELETE w FROM work_packages w JOIN projects p ON w.project_id = p.id "
                + "WHERE p.title LIKE '" + PREFIX + "%'");
        runSilent("DELETE FROM projects WHERE title LIKE '" + PREFIX + "%'");
        try { db.close(); } catch (Exception ignored) {}
    }

    static void runSilent(String sql) {
        try (var st = db.createStatement()) { st.executeUpdate(sql); }
        catch (Exception ignored) {}
    }

    // ---------- login ----------

    static void login() throws Exception {
        HttpResponse<String> r = get("/dev-login-collaborator");
        check("login: 302",                 r.statusCode() == 302);
        check("login: redirige a home-collaborator", location(r).contains("/home-collaborator"));

        CookieManager cm = (CookieManager) client.cookieHandler().orElseThrow();
        System.out.println("   cookies tras login: " + cm.getCookieStore().getCookies());
    }

    // ---------- tests ----------

    static void testSaveWorkedHours() throws Exception {
        System.out.println("\n--- SaveWorkedHours ---");

        // Camino feliz
        HttpResponse<String> r = post("/save-worked-hours", form(
                "task_id",     String.valueOf(assignedTaskId),
                "month_index", "1",
                "hours",       "5"));
        check("happy: 302",                       r.statusCode() == 302);
        check("happy: location lleva successMsg", location(r).contains("successMsg"));

        // Sobreescritura
        r = post("/save-worked-hours", form(
                "task_id",     String.valueOf(assignedTaskId),
                "month_index", "1",
                "hours",       "12"));
        check("overwrite: 302",                          r.statusCode() == 302);
        check("overwrite: location lleva successMsg",    location(r).contains("successMsg"));

        // Hours negativas
        r = post("/save-worked-hours", form(
                "task_id",     String.valueOf(assignedTaskId),
                "month_index", "1",
                "hours",       "-3"));
        check("negative hours: 200 (forward)", r.statusCode() == 200);

        // Hours no numéricas
        r = post("/save-worked-hours", form(
                "task_id",     String.valueOf(assignedTaskId),
                "month_index", "1",
                "hours",       "abc"));
        check("non-numeric hours: 200", r.statusCode() == 200);

        // Mes fuera del rango del task
        r = post("/save-worked-hours", form(
                "task_id",     String.valueOf(assignedTaskId),
                "month_index", "8",
                "hours",       "5"));
        check("month out of task range: 200", r.statusCode() == 200);

        // Mes no numérico
        r = post("/save-worked-hours", form(
                "task_id",     String.valueOf(assignedTaskId),
                "month_index", "xyz",
                "hours",       "5"));
        check("non-numeric month: 200", r.statusCode() == 200);

        // Task NO asignado al colaborador
        r = post("/save-worked-hours", form(
                "task_id",     String.valueOf(unassignedTaskId),
                "month_index", "1",
                "hours",       "5"));
        check("unassigned task: 403", r.statusCode() == 403);

        // Task en proyecto CREATED
        r = post("/save-worked-hours", form(
                "task_id",     String.valueOf(createdProjTaskId),
                "month_index", "1",
                "hours",       "5"));
        check("task in CREATED project: 200", r.statusCode() == 200);

        // taskId inexistente
        r = post("/save-worked-hours", form(
                "task_id",     "99999",
                "month_index", "1",
                "hours",       "5"));
        check("nonexistent taskId: 400 o similar (no 302)", r.statusCode() != 302);

        // taskId no numérico
        r = post("/save-worked-hours", form(
                "task_id",     "abc",
                "month_index", "1",
                "hours",       "5"));
        check("bad taskId: 400 o similar (no 302)", r.statusCode() != 302);
    }

    // ---------- main ----------

    public static void main(String[] args) throws Exception {
        try {
            setup();
            login();
            testSaveWorkedHours();
        } finally {
            teardown();
        }
        System.out.printf("%n=== %d OK / %d FAIL ===%n", ok, fail);
    }
}