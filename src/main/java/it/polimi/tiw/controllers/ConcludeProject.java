package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.HoursDAO;
import it.polimi.tiw.dao.ProjectDAO;


@WebServlet("/conclude-project")
public class ConcludeProject extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	
        HttpSession session = request.getSession(false);
        User manager = (User) session.getAttribute("user");

        Integer projectId = tryParse(request.getParameter("projectId"));
        if (projectId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Project not specified");
            return;
        }

        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            Project project = projectDAO.findById(projectId);
            if (project == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Nonexistent project");
                return;
            }
            if (project.getManagerId() != manager.getId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "You are not the manager of this project");
                return;
            }
            if (!project.isAssigned()) {
                fail(request, response, projectId, "Only a project in ASSIGNED state can be concluded");
                return;
            }
            if (!hoursDAO.canConcludeProject(projectId)) {
                fail(request, response, projectId, "Impossible to conclude: for some tasts the worked hours are inferior to the planned ones");
                return;
            }
            projectDAO.updateState(projectId, "CONCLUDED");
            String msg = URLEncoder.encode("Project \"" + project.getTitle() + "\" concluded.", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/monitor-projects?projectId=" + projectId + "&successMsg=" + msg);
        } catch (SQLException e) {
            throw new ServletException("Error during the conclusion of the project", e);
        }
    }

    
    
    private void fail(HttpServletRequest request, HttpServletResponse response, int projectId, String errorMsg) throws ServletException, IOException {
        request.setAttribute("errorMsg", errorMsg);
        request.getRequestDispatcher("/monitor-projects?projectId=" + projectId)
               .forward(request, response);
    }

    private Integer tryParse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}