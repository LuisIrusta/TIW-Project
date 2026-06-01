package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
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
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.dao.WorkPackageDAO;
import it.polimi.tiw.dao.WorkedHoursDAO;

@WebServlet("/monitoraggioCollaboratori")
public class GoToMonitoraggioCollaboratori extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || !PersonType.TECHNICAL.equals(session.getAttribute("role"))) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        User manager  = (User) session.getAttribute("user");
        int collabId  = parseIntOrDefault(request.getParameter("collabId"), 0);

        try {
            UserDAO        userDAO    = new UserDAO(connection);
            ProjectDAO     projectDAO = new ProjectDAO(connection);
            WorkPackageDAO wpDAO      = new WorkPackageDAO(connection);
            TaskDAO        taskDAO    = new TaskDAO(connection);
            WorkedHoursDAO whDAO      = new WorkedHoursDAO(connection);

            List<User> collaboratori = userDAO.findCollaboratorsByManager(manager.getId());
            User collaboratore = collaboratori.stream().filter(c -> c.getId() == collabId).findFirst().orElse(null);

            List<Map<String,Object>> progettiData = new ArrayList<>();

            if (collaboratore != null) {
                for (Project prog : projectDAO.findByCollaborator(collabId)) {
                    if (prog.getManagerId() != manager.getId()) continue;
                    List<Map<String,Object>> wpsData = new ArrayList<>();
                    for (WorkPackage wp : wpDAO.findByProjectAndCollaborator(prog.getId(), collabId)) {
                        List<Map<String,Object>> tasksData = new ArrayList<>();
                        for (Task task : taskDAO.findByWpAndCollaborator(wp.getId(), collabId)) {
                            Map<String,Object> td = new LinkedHashMap<>();
                            td.put("task", task);
                            td.put("ore",  whDAO.findByTaskAndCollaborator(task.getId(), collabId));
                            tasksData.add(td);
                        }
                        Map<String,Object> wpEntry = new LinkedHashMap<>();
                        wpEntry.put("wp", wp); wpEntry.put("tasks", tasksData);
                        wpsData.add(wpEntry);
                    }
                    Map<String,Object> progEntry = new LinkedHashMap<>();
                    progEntry.put("progetto", prog); progEntry.put("wps", wpsData);
                    progettiData.add(progEntry);
                }
            }

            WebContext ctx = getWebContext(request, response);
            ctx.setVariable("manager",       manager);
            ctx.setVariable("collaboratori", collaboratori);
            ctx.setVariable("collaboratore", collaboratore);
            ctx.setVariable("progettiData",  progettiData);
            ctx.setVariable("collabId",      collabId);

            templateEngine.process("monitoraggioCollaboratori", ctx, response.getWriter());

        } catch (SQLException e) {
            throw new ServletException("Error loading monitoraggio collaboratori", e);
        }
    }
}