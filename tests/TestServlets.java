package it.polimi.tiw.tests;

import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.stream.Collectors;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.*;

public class TestServlets {

    // ===== ADAPTA ESTOS 4 VALORES A TU ENTORNO =====
    static final String BASE    = "http://localhost:8080/TIW-Project";   // p.ej. http://localhost:8080/ProgettoRendicontazione
    static final String DB_URL  = "jdbc:mysql://localhost:3306/TIW_DB?serverTimezone=UTC";
    static final String DB_USER = "luis";
    static final String DB_PASS = "Password@2026";

    static final String PREFIX = "TEST_" + System.currentTimeMillis() + "_";

    static HttpClient client;
    static Connection db;
    static int ok = 0, fail = 0;

    static User admin;
    static int admin2Id, tecnicoId, collaboratorId;
    static int createdProjId, assignedProjId, admin2ProjId;
    static int createdWpId, assignedWpId, admin2WpId;

    // ===========================================================
    // Helpers
    // ===========================================================

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

    static HttpResponse<String> post(String path, Map<String, String> formParams) throws Exception {
        String body = formParams.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body))
                .build();
        return client.send(req, BodyHandlers.ofString());
    }

    static HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build();
        return client.send(req, BodyHandlers.ofString());
    }

    static String location(HttpResponse<?> r) {
        return r.headers().firstValue("Location").orElse("");
    }

    // ===========================================================
    // Setup / teardown
    // ===========================================================

    static void setup() throws Exception {
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder()
                .cookieHandler(cm)
                .followRedirects(HttpClient.Redirect.NEVER)   // queremos ver los 302
                .build();

        Class.forName("com.mysql.cj.jdbc.Driver");
        db = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);

        UserDAO userDAO = new UserDAO(db);
        ProjectDAO projectDAO = new ProjectDAO(db);
        WorkPackageDAO wpDAO = new WorkPackageDAO(db);
        TaskDAO taskDAO = new TaskDAO(db); 

        admin = userDAO.checkCredentials("admin1", "admin1");
        if (admin == null) throw new RuntimeException("admin1 no existe");
        User admin2 = userDAO.checkCredentials("admin2", "admin2");
        if (admin2 == null) throw new RuntimeException("admin2 no existe");
        admin2Id = admin2.getId();

        List<User> tecnicos = userDAO.findAllTechnicals();
        if (tecnicos.size() < 2) {
            throw new RuntimeException("se necesitan al menos 2 TECNICOs en la BD");
        }
        tecnicoId      = tecnicos.get(0).getId();   // responsable de los proyectos fixture
        collaboratorId = tecnicos.get(1).getId();   // colaborador del task fixture (≠ responsable)

        // Tres proyectos fixture
     // Tres proyectos fixture (todos nacen en CREATED)
        createdProjId  = projectDAO.createProject(PREFIX + "Created",  24, admin.getId(), tecnicoId);
        assignedProjId = projectDAO.createProject(PREFIX + "Assigned", 24, admin.getId(), tecnicoId);
        admin2ProjId   = projectDAO.createProject(PREFIX + "Admin2",   24, admin2Id,      tecnicoId);

        // Un WP por proyecto (necesario antes de poder asignar)
        createdWpId  = wpDAO.createWorkPackage(createdProjId,  PREFIX + "WP",  1, 6);
        assignedWpId = wpDAO.createWorkPackage(assignedProjId, PREFIX + "WP",  1, 6);
        admin2WpId   = wpDAO.createWorkPackage(admin2ProjId,   PREFIX + "WP",  1, 6);
        
        int assignedTaskId = taskDAO.createTask(assignedWpId, PREFIX + "T", "fixture task", 1, 6);
        
     // Asignar un colaborador al task
        try (var ps = db.prepareStatement(
                "INSERT INTO task_assignments (task_id, collaborator_id) VALUES (?, ?)")) {
            ps.setInt(1, assignedTaskId);
            ps.setInt(2, collaboratorId);
            ps.executeUpdate();
        }

        // Horas previstas para cada mes del task (1..6)
        try (var ps = db.prepareStatement(
                "INSERT INTO planned_hours (task_id, month_index, hours) VALUES (?, ?, ?)")) {
            for (int m = 1; m <= 6; m++) {
                ps.setInt(1, assignedTaskId);
                ps.setInt(2, m);
                ps.setInt(3, 10);
                ps.executeUpdate();
            }
        }

        // AHORA sí podemos pasar el segundo a ASSIGNED (ya tiene WP)
        projectDAO.updateState(assignedProjId, "ASSIGNED");

        System.out.println("Fixtures: created=" + createdProjId + " assigned=" + assignedProjId
                + " admin2=" + admin2ProjId);
    }

    static void teardown() {
        if (db == null) return;
        try (var st = db.createStatement()) {
            // PASO 1: devolver proyectos TEST_* a CREATED para que se puedan borrar sus elementos
            st.executeUpdate("UPDATE projects SET state = 'CREATED' "
                    + "WHERE title LIKE '" + PREFIX + "%'");

            // PASO 2 (si no tienes ON DELETE CASCADE): borrar las filas hijas explícitamente
            st.executeUpdate("DELETE ph FROM planned_hours ph "
                    + "JOIN tasks t ON ph.task_id = t.id "
                    + "JOIN work_packages w ON t.wp_id = w.id "
                    + "JOIN projects p ON w.project_id = p.id "
                    + "WHERE p.title LIKE '" + PREFIX + "%'");
            st.executeUpdate("DELETE a FROM task_assignments a "
                    + "JOIN tasks t ON a.task_id = t.id "
                    + "JOIN work_packages w ON t.wp_id = w.id "
                    + "JOIN projects p ON w.project_id = p.id "
                    + "WHERE p.title LIKE '" + PREFIX + "%'");

            // PASO 3: borrar tasks, WPs y proyectos en cascada manual
            st.executeUpdate("DELETE t FROM tasks t "
                    + "JOIN work_packages w ON t.wp_id = w.id "
                    + "JOIN projects p ON w.project_id = p.id "
                    + "WHERE p.title LIKE '" + PREFIX + "%'");
            st.executeUpdate("DELETE w FROM work_packages w "
                    + "JOIN projects p ON w.project_id = p.id "
                    + "WHERE p.title LIKE '" + PREFIX + "%'");
            st.executeUpdate("DELETE FROM projects WHERE title LIKE '" + PREFIX + "%'");

            db.close();
        } catch (Exception e) {
            System.err.println("teardown error: " + e.getMessage());
        }
    }

    static void login() throws Exception {
        HttpResponse<String> r = get("/dev-login-admin");
        System.out.println(">> login status = " + r.statusCode());
        System.out.println(">> login location = " + location(r));
        System.out.println(">> login body inicio = "
                + r.body().substring(0, Math.min(200, r.body().length())));
        check("login: 302",                 r.statusCode() == 302);
        check("login: redirige a home-admin", location(r).contains("/home-admin"));
    }

    // ===========================================================
    // CreateProject
    // ===========================================================

    static void testCreateProject() throws Exception {
        System.out.println("\n--- CreateProject ---");

        // Camino feliz
        HttpResponse<String> r = post("/create-project", form(
                "title", PREFIX + "OK",
                "durationMonth", "12",
                "managerId", String.valueOf(tecnicoId)));
        check("happy: 302",                       r.statusCode() == 302);
        check("happy: location lleva successMsg", location(r).contains("successMsg"));

        // Título vacío
        r = post("/create-project", form(
                "title", "",
                "durationMonth", "12",
                "managerId", String.valueOf(tecnicoId)));
        check("empty title: 200 (forward)", r.statusCode() == 200);

        // Duración no numérica
        r = post("/create-project", form(
                "title", PREFIX + "BadDur",
                "durationMonth", "abc",
                "managerId", String.valueOf(tecnicoId)));
        check("bad duration: 200", r.statusCode() == 200);

        // Duración negativa
        r = post("/create-project", form(
                "title", PREFIX + "NegDur",
                "durationMonth", "-5",
                "managerId", String.valueOf(tecnicoId)));
        check("negative duration: 200", r.statusCode() == 200);

        // Responsable inexistente
        r = post("/create-project", form(
                "title", PREFIX + "NoResp",
                "durationMonth", "12",
                "managerId", "99999"));
        check("non-existent responsible: 200", r.statusCode() == 200);

        // Responsable es AMMINISTRATIVO (admin1 a sí mismo). Debe rechazarlo.
        r = post("/create-project", form(
                "title", PREFIX + "RespIsAdmin",
                "durationMonth", "12",
                "managerId", String.valueOf(admin.getId())));
        check("manager is admin: 200", r.statusCode() == 200);
    }

    // ===========================================================
    // CreateWorkPackage
    // ===========================================================

    static void testCreateWp() throws Exception {
        System.out.println("\n--- CreateWorkPackage ---");

        // Camino feliz
        HttpResponse<String> r = post("/create-wp", form(
                "projectId", String.valueOf(createdProjId),
                "title",     PREFIX + "WPok",
                "startMonth", "2",
                "endMonth",   "5"));
        check("happy: 302",                       r.statusCode() == 302);
        check("happy: location lleva successMsg", location(r).contains("successMsg"));

        // Título vacío
        r = post("/create-wp", form(
                "projectId", String.valueOf(createdProjId),
                "title",     "",
                "startMonth", "2",
                "endMonth",   "5"));
        check("empty title: 200", r.statusCode() == 200);

        // Proyecto NO tuyo (es de admin2)
        r = post("/create-wp", form(
                "projectId", String.valueOf(admin2ProjId),
                "title",     PREFIX + "WPforeign",
                "startMonth", "1",
                "endMonth",   "3"));
        check("foreign project: 200", r.statusCode() == 200);

        // Proyecto en ASSIGNED (no es CREATED)
        r = post("/create-wp", form(
                "projectId", String.valueOf(assignedProjId),
                "title",     PREFIX + "WPassigned",
                "startMonth", "1",
                "endMonth",   "3"));
        check("non-CREATED project: 200", r.statusCode() == 200);

        // Mes fuera de rango (proyecto dura 24, pedimos 30)
        r = post("/create-wp", form(
                "projectId", String.valueOf(createdProjId),
                "title",     PREFIX + "WPbadmonth",
                "startMonth", "1",
                "endMonth",   "30"));
        check("month out of range: 200", r.statusCode() == 200);
    }

    // ===========================================================
    // CreateTask
    // ===========================================================

    static void testCreateTask() throws Exception {
        System.out.println("\n--- CreateTask ---");

        // Camino feliz: WP del proyecto CREATED de admin1, meses 2-5 dentro de WP (1-6)
        HttpResponse<String> r = post("/create-task", form(
                "wpId",        String.valueOf(createdWpId),
                "title",       PREFIX + "Tok",
                "description", "desc",
                "startMonth",  "2",
                "endMonth",    "5"));
        check("happy: 302",                       r.statusCode() == 302);
        check("happy: location lleva successMsg", location(r).contains("successMsg"));

        // Título vacío
        r = post("/create-task", form(
                "wpId",        String.valueOf(createdWpId),
                "title",       "",
                "description", "desc",
                "startMonth",  "2",
                "endMonth",    "5"));
        check("empty title: 200", r.statusCode() == 200);

        // WP de proyecto NO tuyo (admin2)
        r = post("/create-task", form(
                "wpId",        String.valueOf(admin2WpId),
                "title",       PREFIX + "Tforeign",
                "description", "desc",
                "startMonth",  "1",
                "endMonth",    "3"));
        check("WP from foreign project: 200", r.statusCode() == 200);

        // WP de proyecto ASSIGNED
        r = post("/create-task", form(
                "wpId",        String.valueOf(assignedWpId),
                "title",       PREFIX + "Tassigned",
                "description", "desc",
                "startMonth",  "1",
                "endMonth",    "3"));
        check("WP from non-CREATED project: 200", r.statusCode() == 200);

        // Mes del task fuera del rango del WP (1-6) → pedimos 8
        r = post("/create-task", form(
                "wpId",        String.valueOf(createdWpId),
                "title",       PREFIX + "TbadMonth",
                "description", "desc",
                "startMonth",  "1",
                "endMonth",    "8"));
        check("task month out of WP range: 200", r.statusCode() == 200);
    }

    // ===========================================================
    // main
    // ===========================================================

    public static void main(String[] args) throws Exception {
        try {
            setup();
            login();
            testCreateProject();
            testCreateWp();
            testCreateTask();
        } finally {
            teardown();
        }
        System.out.printf("%n=== %d OK / %d FAIL ===%n", ok, fail);
    }
}