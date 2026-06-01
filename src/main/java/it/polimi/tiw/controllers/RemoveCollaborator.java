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

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskAssignmentDAO;

@WebServlet("/rimuovi-collaboratore")
public class RemoveCollaborator extends AbstractServlet {

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
        int taskId   = parseIntOrDefault(request.getParameter("taskId"), 0);
        int userId   = parseIntOrDefault(request.getParameter("userId"), 0);
        int projId   = parseIntOrDefault(request.getParameter("projId"), 0);
        int wpId     = parseIntOrDefault(request.getParameter("wpId"),   0);

        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            Project progetto = projectDAO.findByIdAndManager(projId, manager.getId());

            if (progetto == null || !progetto.isCreated()) {
                request.setAttribute("errorMsg", "Operation not allowed.");
                request.getRequestDispatcher("/homeResponsabile?projId=" + projId +
                    "&wpId=" + wpId + "&taskId=" + taskId).forward(request, response);
                return;
            }

            new TaskAssignmentDAO(connection).remove(taskId, userId);
            String msg = URLEncoder.encode("Collaborator removed.", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() +
                "/homeResponsabile?projId=" + projId + "&wpId=" + wpId +
                "&taskId=" + taskId + "&successMsg=" + msg);

        } catch (SQLException e) {
            throw new ServletException("Error removing collaborator", e);
        }
    }
}