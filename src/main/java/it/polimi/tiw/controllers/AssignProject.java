package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.HoursDAO;
import it.polimi.tiw.dao.ProjectDAO;


@WebServlet("/api/assign-project")
public class AssignProject extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendJsonResponse(response, 401, Map.of("error", "Sessione scaduta o non valida. Effettua il login."));
            return;
        }
        User manager = (User) session.getAttribute("user");
        if (!manager.isTechnical()) { 
            sendJsonResponse(response, 403, Map.of("error", "Accesso negato. Non hai i permessi necessari."));
            return;
        }
        JsonObject jsonRequest = readJsonRequest(request);
        if (jsonRequest == null) {
            sendJsonResponse(response, 400, Map.of("success", false, "error", "Payload JSON mancante o malformato."));
            return;
        }
        Integer projectId = null;
        if (jsonRequest.has("projectId") && !jsonRequest.get("projectId").isJsonNull()) {
            try {
                projectId = jsonRequest.get("projectId").getAsInt();
            } catch (Exception e) {
                sendError(response, 400, "L'identificativo 'projectId' deve essere un numero intero.");
                return;
            }
        }
        if (projectId == null) {
        	sendError(response, 400, "Project not specified");
            return;
        }
        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            Project project = projectDAO.findById(projectId);
            if (project == null) {
            	 sendError(response, 404, "Nonexistent project");
                return;
            }
            if (project.getManagerId() != manager.getId()) {
            	sendError(response, 403, "You are not the manager of this project");
                return;
            }
            if (!project.isCreated()) {
            	sendError(response, 400, "The project is not in CREATED state");
                return;
            }
            List<String> blockers = hoursDAO.getAssignmentBlockers(projectId);
            if (!blockers.isEmpty()) {
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("success", false);
                errorDetails.put("error", "Cannot assign the project: some tasks are not fully configured.");
                errorDetails.put("blockers", blockers);
                sendJsonResponse(response, 400, errorDetails);
                return;
            }
            projectDAO.updateState(projectId, "ASSIGNED");
            String msg = URLEncoder.encode("Project \"" + project.getTitle() + "\" ASSIGNED.", StandardCharsets.UTF_8);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", msg);
            result.put("projectId", projectId);
            sendJsonResponse(response, 200, result);
        } catch (SQLException e) {
            sendError(response, 500, "Error during the assignation of the project" + e.getMessage());
        }
        }
    }
    
   