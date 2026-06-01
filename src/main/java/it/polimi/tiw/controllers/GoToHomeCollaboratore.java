package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.thymeleaf.context.WebContext;

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.Task;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.PlannedHoursDAO;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.WorkPackageDAO;
import it.polimi.tiw.dao.WorkedHoursDAO;

@WebServlet("/homeCollaboratore")
public class GoToHomeCollaboratore extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        showPage(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        showPage(request, response);
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || !PersonType.COLLABORATOR.equals(session.getAttribute("role"))) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        User collaboratore = (User) session.getAttribute("user");
        int projId  = parseIntOrDefault(request.getParameter("projId"),  0);
        int wpId    = parseIntOrDefault(request.getParameter("wpId"),    0);
        int taskId  = parseIntOrDefault(request.getParameter("taskId"),  0);

        try {
            ProjectDAO      projectDAO = new ProjectDAO(connection);
            WorkPackageDAO  wpDAO      = new WorkPackageDAO(connection);
            TaskDAO         taskDAO    = new TaskDAO(connection);
            PlannedHoursDAO phDAO      = new PlannedHoursDAO(connection);
            WorkedHoursDAO  whDAO      = new WorkedHoursDAO(connection);

            List<Project> progetti = projectDAO.findByCollaborator(collaboratore.getId());
            Project progetto = progetti.stream()
                .filter(p -> p.getId() == projId).findFirst().orElse(null);

            List<WorkPackage> wps = progetto != null
                ? wpDAO.findByProjectAndCollaborator(projId, collaboratore.getId())
                : Collections.emptyList();
            WorkPackage wpSel = wps.stream()
                .filter(w -> w.getId() == wpId).findFirst().orElse(null);

            List<Task> tasks = wpSel != null
                ? taskDAO.findByWpAndCollaborator(wpId, collaboratore.getId())
                : Collections.emptyList();
            Task task = tasks.stream()
                .filter(t -> t.getId() == taskId).findFirst().orElse(null);

            int orePrevTot = task != null ? phDAO.getTotalByTask(taskId) : 0;
            Map<Integer,Integer> oreLavorate = task != null
                ? whDAO.findByTaskAndCollaborator(taskId, collaboratore.getId())
                : Collections.emptyMap();
            int oreLavTot = oreLavorate.values().stream().mapToInt(Integer::intValue).sum();

            WebContext ctx = getWebContext(request, response);
            ctx.setVariable("collaboratore",     collaboratore);
            ctx.setVariable("progetti",          progetti);
            ctx.setVariable("progetto",          progetto);
            ctx.setVariable("wps",               wps);
            ctx.setVariable("wpSel",             wpSel);
            ctx.setVariable("tasks",             tasks);
            ctx.setVariable("task",              task);
            ctx.setVariable("orePrevisteTotali", orePrevTot);
            ctx.setVariable("oreLavorate",       oreLavorate);
            ctx.setVariable("oreLavorateTotali", oreLavTot);
            ctx.setVariable("projId",            projId);
            ctx.setVariable("wpId",              wpId);
            ctx.setVariable("taskId",            taskId);
            ctx.setVariable("errorMsg",          request.getAttribute("errorMsg"));
            ctx.setVariable("successMsg",        request.getParameter("successMsg"));
            ctx.setVariable("formData",          request.getAttribute("formData") != null
                                                     ? request.getAttribute("formData")
                                                     : Collections.emptyMap());

            templateEngine.process("homeCollaboratore", ctx, response.getWriter());

        } catch (SQLException e) {
            throw new ServletException("Error loading home collaboratore", e);
        }
    }
}