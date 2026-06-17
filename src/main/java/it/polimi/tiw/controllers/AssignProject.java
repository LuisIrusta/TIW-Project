package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.HoursDAO;
import it.polimi.tiw.dao.ProjectDAO;


@WebServlet("/assign-project")
public class AssignProject extends AbstractServlet {

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
            if (!project.isCreated()) {
                fail(request, response, "The project is not in CREATED state");
                return;
            }
            List<String> blockers = hoursDAO.getAssignmentBlockers(projectId);
            if (!blockers.isEmpty()) {
                fail(request, response, "Impossible to assign the project. " + String.join(" ", blockers));
                return;
            }
            projectDAO.updateState(projectId, "ASSIGNED");
            String msg = URLEncoder.encode( "Project \"" + project.getTitle() + "\" assigned successfully", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/home-manager?successMsg=" + msg);
        } catch (SQLException e) {
            throw new ServletException("Error during the assignation of the project", e);
        }
    }
    
    
    
    private void fail(HttpServletRequest request, HttpServletResponse response, String errorMsg) throws ServletException, IOException {
        request.setAttribute("errorMsg", errorMsg);
        request.getRequestDispatcher("/home-manager").forward(request, response);
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