package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

@WebServlet("/monitoraggioProgetti")
public class GoToMonitoraggioProgetti extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || !PersonType.TECHNICAL.equals(session.getAttribute("role"))) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        User manager = (User) session.getAttribute("user");
        int projId   = parseIntOrDefault(request.getParameter("projId"), 0);

        try {
            ProjectDAO     projectDAO = new ProjectDAO(connection);
            WorkPackageDAO wpDAO      = new WorkPackageDAO(connection);
            TaskDAO        taskDAO    = new TaskDAO(connection);
            PlannedHoursDAO phDAO     = new PlannedHoursDAO(connection);
            WorkedHoursDAO  whDAO     = new WorkedHoursDAO(connection);

            List<Project> progetti = projectDAO.findByManager(manager.getId());
            Project progetto = progetti.stream()
                .filter(p -> p.getId() == projId).findFirst().orElse(null);

            List<Map<String,Object>> wpsData = new ArrayList<>();
            boolean allOk = true;

            if (progetto != null) {
                for (WorkPackage wp : wpDAO.findByProject(projId)) {
                    List<Task> tasks = taskDAO.findByWorkPackage(wp.getId());
                    List<Map<String,Object>> tasksData = new ArrayList<>();

                    for (Task task : tasks) {
                        Map<Integer,Integer> prev = phDAO.findByTask(task.getId());
                        Map<Integer,Integer> lav  = whDAO.findTotalByTask(task.getId());

                        int totPrev = prev.values().stream().mapToInt(Integer::intValue).sum();
                        int totLav  = lav.values().stream().mapToInt(Integer::intValue).sum();
                        if (totLav < totPrev) allOk = false;

                        Map<String,Object> td = new LinkedHashMap<>();
                        td.put("task",     task);
                        td.put("previste", prev);
                        td.put("lavorate", lav);
                        tasksData.add(td);
                    }

                    Map<String,Object> wpEntry = new LinkedHashMap<>();
                    wpEntry.put("wp",    wp);
                    wpEntry.put("tasks", tasksData);
                    wpsData.add(wpEntry);
                }
            }

            WebContext ctx = getWebContext(request, response);
            ctx.setVariable("manager",     manager);
            ctx.setVariable("progetti",    progetti);
            ctx.setVariable("progetto",    progetto);
            ctx.setVariable("wpsData",     wpsData);
            ctx.setVariable("canConclude", progetto != null && progetto.isAssigned() && allOk && !wpsData.isEmpty());
            ctx.setVariable("projId",      projId);
            ctx.setVariable("errorMsg",    request.getParameter("errorMsg"));
            ctx.setVariable("successMsg",  request.getParameter("successMsg"));

            templateEngine.process("monitoraggioProgetti", ctx, response.getWriter());

        } catch (SQLException e) {
            throw new ServletException("Error loading monitoraggio progetti", e);
        }
    }
}