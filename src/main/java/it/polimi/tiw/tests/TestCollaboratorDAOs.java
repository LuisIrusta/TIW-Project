package it.polimi.tiw.tests;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Set;

import it.polimi.tiw.beans.*;
import it.polimi.tiw.dao.*;

public class TestCollaboratorDAOs {

    // === ADAPTA ===
    static final String DB_URL  = "jdbc:mysql://localhost:3306/TIW_DB?serverTimezone=UTC";
    static final String DB_USER = "luis";
    static final String DB_PASS = "Password@2026";

    static final String PREFIX = "TEST_COLL_" + System.currentTimeMillis() + "_";

    static Connection db;
    static int ok = 0, fail = 0;

    static int adminId, managerId, collaboratorId;
    static int projectId, wpId, taskId;

    static void check(String label, boolean cond) {
        if (cond) { ok++; System.out.println("OK   " + label); }
        else      { fail++; System.out.println("FAIL " + label); }
    }

    public static void main(String[] args) throws Exception {
        try {
            setup();
            test_findByCollaboratore();
            test_findByProjectAndCollaboratore();
            test_findByWorkPackageAndCollaboratore();
            test_getCollaboratorIdsOfTask();
            test_getTotalPlannedForTask();
            test_getTotalWorkedForCollaboratorTask();
            test_saveWorkedHours();
        } finally {
            teardown();
        }
        System.out.printf("%n=== %d OK / %d FAIL ===%n", ok, fail);
    }

    // ===== Setup =====

    static void setup() throws Exception {
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
        if (tecnicos.size() < 2) throw new RuntimeException("se necesitan >=2 tecnicos");
        managerId      = tecnicos.get(0).getId();   // responsable del proyecto
        collaboratorId = tecnicos.get(1).getId();   // colaborador del task (≠ manager)

        // Crear estructura completa: proyecto, WP, task
        projectId = projectDAO.createProject(PREFIX + "P", 12, adminId, managerId);
        wpId      = wpDAO.createWorkPackage(projectId, PREFIX + "WP", 1, 6);
        taskId    = taskDAO.createTask(wpId, PREFIX + "T", "fixture", 1, 4);

        // Asignar colaborador al task
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO task_assignments (task_id, collaborator_id) VALUES (?, ?)")) {
            ps.setInt(1, taskId);
            ps.setInt(2, collaboratorId);
            ps.executeUpdate();
        }

        // 10 horas previstas para cada mes del task (1..4) → total esperado = 40
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO planned_hours (task_id, month_index, hours) VALUES (?, ?, ?)")) {
            for (int m = 1; m <= 4; m++) {
                ps.setInt(1, taskId);
                ps.setInt(2, m);
                ps.setInt(3, 10);
                ps.executeUpdate();
            }
        }

        // Pasar a ASSIGNED para poder probar saveWorkedHours (lo bloquea el trigger en CREATED)
        projectDAO.updateState(projectId, "ASSIGNED");

        System.out.println("Fixtures: project=" + projectId + " wp=" + wpId + " task=" + taskId
                + " manager=" + managerId + " collaborator=" + collaboratorId);
    }

    static void teardown() {
        if (db == null) return;
        // Best-effort. El proyecto queda en ASSIGNED y los triggers bloquean borrarlo limpiamente:
        // los TEST_COLL_* van a quedar como residuo. Limpia manualmente con SET FOREIGN_KEY_CHECKS=0
        // de vez en cuando.
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
        try (var st = db.createStatement()) {
            st.executeUpdate(sql);
        } catch (Exception ignored) {}
    }

    // ===== Tests =====

    static void test_findByCollaboratore() throws Exception {
        System.out.println("\n--- findByCollaboratore ---");
        ProjectDAO dao = new ProjectDAO(db);
        List<Project> r = dao.findByCollaborator(collaboratorId);
        check("contains our project",
                r.stream().anyMatch(p -> p.getId() == projectId));

        List<Project> rMgr = dao.findByCollaborator(managerId);
        check("manager NOT seen as collaborator",
                rMgr.stream().noneMatch(p -> p.getId() == projectId));
    }

    static void test_findByProjectAndCollaboratore() throws Exception {
        System.out.println("\n--- findByProjectAndCollaboratore ---");
        WorkPackageDAO dao = new WorkPackageDAO(db);
        List<WorkPackage> r = dao.findByProjectAndCollaborator(projectId, collaboratorId);
        check("contains our WP", r.stream().anyMatch(wp -> wp.getId() == wpId));
        check("only one WP returned", r.size() == 1);

        List<WorkPackage> rMgr = dao.findByProjectAndCollaborator(projectId, managerId);
        check("manager has no WPs as collaborator", rMgr.isEmpty());
    }

    static void test_findByWorkPackageAndCollaboratore() throws Exception {
        System.out.println("\n--- findByWorkPackageAndCollaboratore ---");
        TaskDAO dao = new TaskDAO(db);
        List<Task> r = dao.findByWorkPackageAndCollaborator(wpId, collaboratorId);
        
        check("contains our task", r.stream().anyMatch(t -> t.getId() == taskId));

        // Adapta el getter al nombre real de tu bean Task
        Task t = r.stream().filter(x -> x.getId() == taskId).findFirst().orElseThrow();
        check("total planned = 40 (4 meses * 10h)", t.getTotalPlannedHours() == 40);
        check("total worked  = 0 (todavía)",        t.getTotalWorkedHours()  == 0);

        List<Task> rMgr = dao.findByWorkPackageAndCollaborator(wpId, managerId);
        check("manager has no tasks here", rMgr.isEmpty());
    }

    static void test_getCollaboratorIdsOfTask() throws Exception {
        System.out.println("\n--- getCollaboratorIdsOfTask ---");
        HoursDAO dao = new HoursDAO(db);
        Set<Integer> ids = dao.getCollaboratorIdsOfTask(taskId);
        check("contains our collaborator", ids.contains(collaboratorId));
        check("does NOT contain manager",  !ids.contains(managerId));
    }

    static void test_getTotalPlannedForTask() throws Exception {
        System.out.println("\n--- getTotalPlannedForTask ---");
        HoursDAO dao = new HoursDAO(db);
        check("total planned = 40", dao.getTotalPlannedForTask(taskId) == 40);
    }

    static void test_getTotalWorkedForCollaboratorTask() throws Exception {
        System.out.println("\n--- getTotalWorkedForCollaboratorTask ---");
        HoursDAO dao = new HoursDAO(db);
        check("inicial = 0", dao.getTotalWorkedForCollaboratorTask(taskId, collaboratorId) == 0);
    }

    static void test_saveWorkedHours() throws Exception {
        System.out.println("\n--- saveWorkedHours ---");
        HoursDAO dao = new HoursDAO(db);

        // Inserta 5 horas en M1
        dao.saveWorkedHours(taskId, collaboratorId, 1, 5);
        check("M1=5 → total = 5",
                dao.getTotalWorkedForCollaboratorTask(taskId, collaboratorId) == 5);

        // SOBREESCRIBE M1 a 8 (no debe sumar a las 5 anteriores)
        dao.saveWorkedHours(taskId, collaboratorId, 1, 8);
        check("M1=8 (sobreescribe) → total = 8 (NO 13)",
                dao.getTotalWorkedForCollaboratorTask(taskId, collaboratorId) == 8);

        // Añade horas en M2
        dao.saveWorkedHours(taskId, collaboratorId, 2, 3);
        check("M1=8 + M2=3 → total = 11",
                dao.getTotalWorkedForCollaboratorTask(taskId, collaboratorId) == 11);

        // Comprueba que el total de horas previstas no cambia tras los inserts
        check("planned sigue = 40 (no se ha tocado)",
                dao.getTotalPlannedForTask(taskId) == 40);
    }
}