package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.dao.WorkPackageDAO;

@WebServlet("/api/home-admin")
public class GoToHomeAdmin extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        showHome(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        showHome(request, response);
    }

    private void showHome(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    Map.of("error", "Sessione scaduta o non valida"));
            return;
        }

        User admin = (User) session.getAttribute("user");
        if (!admin.isAdministrative()) { 
            sendJsonResponse(response, 403, Map.of("error", "Accesso negato. Non hai i permessi necessari."));
            return;
        }

        try {
            UserDAO       userDAO    = new UserDAO(connection);
            ProjectDAO    projectDAO = new ProjectDAO(connection);
            WorkPackageDAO wpDAO     = new WorkPackageDAO(connection);
            TaskDAO       taskDAO    = new TaskDAO(connection);

            List<User>    technicals      = userDAO.findAllTechnicals();
            List<Project> createdProjects = projectDAO.findCreatedByAdmin(admin.getId());

            for (Project p : createdProjects) {
                List<WorkPackage> wps = wpDAO.findByProject(p.getId());
                for (WorkPackage wp : wps) {
                    wp.setTasks(taskDAO.findByWorkPackage(wp.getId()));
                }
                p.setWorkPackages(wps);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("admin",          admin);
            result.put("technicals",     technicals);
            result.put("createdProjects", createdProjects);

            sendJsonResponse(response, 200, result);

        } catch (SQLException e) {
            sendError(response, 500, "Error while loading the home administrator page");
        }
    }
}