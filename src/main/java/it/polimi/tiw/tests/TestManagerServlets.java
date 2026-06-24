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

public class TestManagerServlets {

    // === ADAPTA ===
    static final String BASE    = "http://localhost:8080/TIW-Project";
    static final String DB_URL  = "jdbc:mysql://localhost:3306/TIW_DB?serverTimezone=UTC";
    static final String DB_USER = "luis";
    static final String DB_PASS = "Password@2026";

    static final String PREFIX = "TEST_MGR_" + System.currentTimeMillis() + "_";

    static HttpClient client;
    static Connection db;
    static int ok = 0, fail = 0;

    static int adminId, managerId, otherManagerId, collaboratorId, otherCollabId;
    static int createdProjId;       // proyecto en CREATED del manager para SaveAssignment
    static int createdWpId;
    static int createdTaskId;
    static int assignableProjId;    // proyecto en CREATED listo para asignar (con asignación + horas)
    static int assignableTaskId;
    static int assignedProjId;      // proyecto en ASSIGNED para ConcludeProject (con horas trabajadas)
    static int assignedTaskId;

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

    static HttpResponse<String> postRaw(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> r = client.send(req, BodyHandlers.ofString());
        System.out.println("   POST " + path + " -> status=" + r.statusCode()
                + " location=" + location(r));
        if (r.statusCode() >= 500) {
            String body2 = r.body();
            System.out.println("   body: " + (body2.length() > 200 ? body2.substring(0, 200) + "..." : body2));
        }
        return r;
    }

    static HttpResponse<String> post(String path, Map<String, String> params) throws Exception {
        String body = params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        return postRaw(path, body);
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
        if (tecnicos.size() < 4) throw new RuntimeException("se necesitan >=4 tecnicos");
        managerId      = tecnicos.get(0).getId();   // manager del DevLogin (tecnicos.get(0))
        collaboratorId = tecnicos.get(1).getId();
        otherCollabId  = tecnicos.get(2).getId();
        otherManagerId = tecnicos.get(3).getId();   // manager de otro proyecto (para tampering)

        // ---- Proyecto CREATED para probar SaveAssignment ----
        createdProjId = projectDAO.createProject(PREFIX + "Pcreated", 12, adminId, managerId);
        createdWpId = wpDAO.createWorkPackage(createdProjId, PREFIX + "WP", 1, 4);
        createdTaskId = taskDAO.createTask(createdWpId, PREFIX + "T", "fixture", 1, 3);

        // ---- Proyecto CREATED listo para asignar (con asignación + horas) ----
        assignableProjId = projectDAO.createProject(PREFIX + "Passignable", 6, adminId, managerId);
        int wpAssignable = wpDAO.createWorkPackage(assignableProjId, PREFIX + "WPa", 1, 3);
        assignableTaskId = taskDAO.createTask(wpAssignable, PREFIX + "Ta", "fixture", 1, 3);
        insertAssignment(assignableTaskId, collaboratorId);
        insertPlannedHours(assignableTaskId, 1, 3, 10);

        // ---- Proyecto ASSIGNED con horas trabajadas suficientes para concluir ----
        assignedProjId = projectDAO.createProject(PREFIX + "Passigned", 6, adminId, managerId);
        int wpAssigned = wpDAO.createWorkPackage(assignedProjId, PREFIX + "WPas", 1, 3);
        assignedTaskId = taskDAO.createTask(wpAssigned, PREFIX + "Tas", "fixture", 1, 3);
        insertAssignment(assignedTaskId, collaboratorId);
        insertPlannedHours(assignedTaskId, 1, 3, 5);
        projectDAO.updateState(assignedProjId, "ASSIGNED");
        // Horas trabajadas = previstas (justo lo necesario para concluir)
        insertWorkedHours(assignedTaskId, collaboratorId, 1, 5);
        insertWorkedHours(assignedTaskId, collaboratorId, 2, 5);
        insertWorkedHours(assignedTaskId, collaboratorId, 3, 5);

        System.out.println("Fixtures: managerId=" + managerId
                + " otherManagerId=" + otherManagerId
                + " collaboratorId=" + collaboratorId
                + " createdProjId=" + createdProjId + " createdTaskId=" + createdTaskId
                + " assignableProjId=" + assignableProjId + " assignableTaskId=" + assignableTaskId
                + " assignedProjId=" + assignedProjId + " assignedTaskId=" + assignedTaskId);
    }

    static void insertAssignment(int taskId, int collabId) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO task_assignments (task_id, collaborator_id) VALUES (?, ?)")) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            ps.executeUpdate();
        }
    }

    static void insertPlannedHours(int taskId, int from, int to, int hours) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO planned_hours (task_id, month_index, hours) VALUES (?, ?, ?)")) {
            for (int m = from; m <= to; m++) {
                ps.setInt(1, taskId);
                ps.setInt(2, m);
                ps.setInt(3, hours);
                ps.executeUpdate();
            }
        }
    }

    static void insertWorkedHours(int taskId, int collabId, int month, int hours) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO worked_hours (task_id, collaborator_id, month_index, hours) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            ps.setInt(3, month);
            ps.setInt(4, hours);
            ps.executeUpdate();
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
        HttpResponse<String> r = get("/dev-login-manager");
        check("login: 302", r.statusCode() == 302);
        check("login: redirige a home-manager",
                location(r).contains("/home-manager"));
    }

    // ---------- tests ----------

    static void testSaveAssignment() throws Exception {
        System.out.println("\n--- SaveAssignment ---");

        // Camino feliz: asignar colaborador + horas previstas a un task de un proyecto CREATED del manager
        HttpResponse<String> r = post("/save-assignment", form(
                "taskId",         String.valueOf(createdTaskId),
                "collaborators",  String.valueOf(collaboratorId),
                "hours1",         "8",
                "hours2",         "9",
                "hours3",         "10"));
        check("happy: 302", r.statusCode() == 302);
        check("happy: location lleva successMsg", location(r).contains("successMsg"));

        // Sobreescritura: vuelve a guardar con otro colaborador y otras horas
        r = post("/save-assignment", form(
                "taskId",         String.valueOf(createdTaskId),
                "collaborators",  String.valueOf(otherCollabId),
                "hours1",         "4",
                "hours2",         "5",
                "hours3",         "6"));
        check("overwrite: 302", r.statusCode() == 302);

        // Autoasignación rechazada (manager intentando ser su propio colaborador)
        r = post("/save-assignment", form(
                "taskId",         String.valueOf(createdTaskId),
                "collaborators",  String.valueOf(managerId),
                "hours1",         "1"));
        check("self-assignment: 200 (forward con error)", r.statusCode() == 200);

        // Horas negativas
        r = post("/save-assignment", form(
                "taskId",         String.valueOf(createdTaskId),
                "collaborators",  String.valueOf(collaboratorId),
                "hours1",         "-3"));
        check("negative hours: 200", r.statusCode() == 200);

        // Horas no numéricas
        r = post("/save-assignment", form(
                "taskId",         String.valueOf(createdTaskId),
                "collaborators",  String.valueOf(collaboratorId),
                "hours1",         "abc"));
        check("non-numeric hours: 200", r.statusCode() == 200);

        // taskId inexistente
        r = post("/save-assignment", form(
                "taskId",         "999999",
                "collaborators",  String.valueOf(collaboratorId),
                "hours1",         "5"));
        check("nonexistent taskId: no 302", r.statusCode() != 302);

        // taskId no numérico
        r = post("/save-assignment", form(
                "taskId",         "abc"));
        check("bad taskId: no 302", r.statusCode() != 302);

        // Task de proyecto ASSIGNED (no debe permitir modificar asignación)
        r = post("/save-assignment", form(
                "taskId",         String.valueOf(assignedTaskId),
                "collaborators",  String.valueOf(collaboratorId),
                "hours1",         "5"));
        check("task in ASSIGNED project: 200 (forward con error)", r.statusCode() == 200);
    }

    static void testAssignProject() throws Exception {
        System.out.println("\n--- AssignProject ---");

        // Camino feliz: assignableProjId está completo (asignación + horas), debe pasar a ASSIGNED
        HttpResponse<String> r = post("/assign-project", form(
                "projectId", String.valueOf(assignableProjId)));
        check("happy: 302", r.statusCode() == 302);
        check("happy: location lleva successMsg", location(r).contains("successMsg"));

        // Reintentar (ya está ASSIGNED, no se puede reasignar)
        r = post("/assign-project", form(
                "projectId", String.valueOf(assignableProjId)));
        check("reassign already-ASSIGNED: 200 (forward error)", r.statusCode() == 200);

        // Proyecto CREATED tras SaveAssignment (puede o no estar completo según
        // lo que dejaran las pruebas de SaveAssignment; comprobamos solo que no peta).
        r = post("/assign-project", form(
                "projectId", String.valueOf(createdProjId)));
        check("createdProjId: no 500", r.statusCode() < 500);

        // projectId inexistente
        r = post("/assign-project", form(
                "projectId", "999999"));
        check("nonexistent: no 302", r.statusCode() != 302);

        // projectId no numérico (sendError 400 esperado)
        r = post("/assign-project", form(
                "projectId", "abc"));
        check("bad projectId: 400", r.statusCode() == 400);
    }

    static void testConcludeProject() throws Exception {
        System.out.println("\n--- ConcludeProject ---");

        // Camino feliz: assignedProjId tiene horas trabajadas == previstas, se puede concluir
        HttpResponse<String> r = post("/conclude-project", form(
                "projectId", String.valueOf(assignedProjId)));
        check("happy: 302", r.statusCode() == 302);
        check("happy: location lleva successMsg", location(r).contains("successMsg"));

        // Reintentar (ya está CONCLUDED, no se puede)
        r = post("/conclude-project", form(
                "projectId", String.valueOf(assignedProjId)));
        check("re-conclude: 200 (forward error)", r.statusCode() == 200);

        // projectId no numérico
        r = post("/conclude-project", form(
                "projectId", "abc"));
        check("bad projectId: 400", r.statusCode() == 400);
    }

    static void testGoToPages() throws Exception {
        System.out.println("\n--- Páginas (solo render, no acciones) ---");
        HttpResponse<String> r;

        r = get("/home-manager");
        check("home-manager: 200", r.statusCode() == 200);

        r = get("/monitor-projects");
        check("monitor-projects: 200", r.statusCode() == 200);

        r = get("/monitor-collaborators");
        check("monitor-collaborators: 200", r.statusCode() == 200);

        // Selección de proyecto en monitor-projects
        r = get("/monitor-projects?projectId=" + assignedProjId);
        check("monitor-projects con proyecto: 200", r.statusCode() == 200);
    }

    // ---------- main ----------

    public static void main(String[] args) throws Exception {
        try {
            setup();
            login();
            testSaveAssignment();
            testAssignProject();
            testConcludeProject();
            testGoToPages();
        } finally {
            teardown();
        }
        System.out.printf("%n=== %d OK / %d FAIL ===%n", ok, fail);
    }
}