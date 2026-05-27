package it.polimi.tiw.tests;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.Task;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.dao.WorkPackageDAO;

public class TestModel {

    static int ok = 0, fail = 0;

    static void check(String label, boolean cond) {
        if (cond) { System.out.println("OK   " + label); ok++; }
        else      { System.out.println("FAIL " + label); fail++; }
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/TIW_DB?serverTimezone=UTC", "luis", "Password@2026")) {

            UserDAO userDAO = new UserDAO(c);
            ProjectDAO projectDAO = new ProjectDAO(c);
            WorkPackageDAO wpDAO = new WorkPackageDAO(c);
            TaskDAO taskDAO = new TaskDAO(c);

            // ----- UserDAO -----
            User u = userDAO.checkCredentials("admin1", "admin1");
            check("login admin1 ok",          u != null && u.isAdministrative());
            check("login admin1 password OK", userDAO.checkCredentials("admin1", "wrong") == null);
            check("login ghost user",      userDAO.checkCredentials("ghost", "x") == null);
            check("findById admin1",          userDAO.findById(u.getId()) != null);
            check("findById nonexistent",     userDAO.findById(99999) == null);
            check("findAllTechnicals = 4",       userDAO.findAllTechnicals().size() == 4);
            check("findAllTechnicals without admin", userDAO.findAllTechnicals().stream().allMatch(User::isTechnical));

            // ----- ProjectDAO + WorkPackageDAO + TaskDAO en cadena -----
            int idAdmin = u.getId();
            int idTec   = userDAO.findAllTechnicals().get(0).getId();

            int idProj = projectDAO.createProject("Test SmokeT", 24, idAdmin, idTec);
            Project p = projectDAO.findById(idProj);
            System.out.println("Proyecto leído: id=" + p.getId()
            + ", titolo=[" + p.getTitle() + "]"
            + ", durataMesi=" + p.getDurationMonths()
            + ", stato=[" + p.getState() + "]"
            + ", idAmm=" + p.getAdministratorId()
            + ", idResp=" + p.getManagerId());
            check("create project roundtrip", p != null && p.isCreated()
                                              && p.getDurationMonths() == 24);

            check("findCreatedByAdmin includes new",
                  projectDAO.findCreatedByAdmin(idAdmin).stream().anyMatch(x -> x.getId() == idProj));
            check("findByAdmin includes new",
                  projectDAO.findByAdmin(idAdmin).stream().anyMatch(x -> x.getId() == idProj));

            int idWp1 = wpDAO.createWorkPackage(idProj, "Analysis",   1, 6);
            int idWp2 = wpDAO.createWorkPackage(idProj, "Development", 5, 18);
            List<WorkPackage> wps = wpDAO.findByProject(idProj);
            check("There are 2 WP",              wps.size() == 2);
            check("WP1 before WP2",     wps.get(0).getOrderNumber() == 1
                                           && wps.get(1).getOrderNumber() == 2);
            check("WP findById coherent", wpDAO.findById(idWp1).getTitle().equals("Analysis"));

            int idT1 = taskDAO.createTask(idWp1, "Requisites", "desc", 1, 3);
            int idT2 = taskDAO.createTask(idWp1, "Design",    "desc", 4, 6);
            List<Task> tasks = taskDAO.findByWorkPackage(idWp1);
            check("There are 2 tasks",      tasks.size() == 2);
            check("T1.1 number 1",    tasks.get(0).getOrderNumber() == 1);
            check("T1.2 number 2",    tasks.get(1).getOrderNumber() == 2);
            check("code T1.1",      "T1.1".equals(tasks.get(0).getCode()));
            check("total initial 0", tasks.get(0).getTotalPlannedHours() == 0
                                         && tasks.get(0).getTotalWorkedHours() == 0);

            // WP sin tasks: lista vacía, no null
            List<Task> vacio = taskDAO.findByWorkPackage(idWp2);
            check("WP without tasks empty list", vacio != null && vacio.isEmpty());

            // Proyecto ya ASSEGNATO de los datos de ejemplo NO debe salir en CreatiByAdmin
            // (el proyecto 2 del data.sql es ASSEGNATO; chequea su admin):
            check("CreatedByAdmin does not include ASSIGNED",
                  projectDAO.findCreatedByAdmin(idAdmin).stream().noneMatch(x -> "ASSIGNED".equals(x.getState())));

            System.out.printf("%n=== Result: %d OK / %d FAIL ===%n", ok, fail);
        }
    }
}