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

import org.thymeleaf.context.WebContext;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.ProjectDAO;


@WebServlet("/api/verifica-progetti")
public class GoToVerificaProgetti extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
         
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, Map.of("error", "Sessione scaduta o non valida"));
            return;
        }
        User admin = (User) session.getAttribute("user");
        if (!admin.isAdministrative()) { 
            sendJsonResponse(response, 403, Map.of("error", "Accesso negato. Non hai i permessi necessari."));
            return;
        }

        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            List<Project> projects = projectDAO.findByAdmin(admin.getId());
            int selectedId = parseIntOrDefault(request.getParameter("projectId"), -1);
            Project selected = null;
            for (Project p : projects) {
                if (p.getId() == selectedId) {
                    selected = p;
                    break;
                }
            }
            if (selected == null && !projects.isEmpty()) {
                selected = projects.get(0);
            }
            if (selected != null) {
                projectDAO.loadStructure(selected);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("admin", admin);
            result.put("progetti",         projects);
            result.put("selected", selected);
            sendJsonResponse(response, 200, result);
        } catch (SQLException e) {
        	sendJsonResponse(response, 500, Map.of("success", false, "error", "Error while loading the page VERIFICA PROGETTI"));
        }
    }
}