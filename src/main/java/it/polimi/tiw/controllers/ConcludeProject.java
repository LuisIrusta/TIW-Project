package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
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


@WebServlet("/api/conclude-project")
public class ConcludeProject extends AbstractServlet {

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
            sendError(response, 404,"Project not specified");
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
            if (!project.isAssigned()) {
                sendError(response, 400, "Only a project in ASSIGNED state can be concluded");
                return;
            }
            if (!hoursDAO.canConcludeProject(projectId)) {
            	sendError(response, 400, "Impossible to conclude: for some tasts the worked hours are inferior to the planned ones");
                return;
            }
            projectDAO.updateState(projectId, "CONCLUDED");
            String msg = URLEncoder.encode("Project \"" + project.getTitle() + "\" concluded.", StandardCharsets.UTF_8);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", msg);
            result.put("projectId", projectId);
            sendJsonResponse(response, 200, result);
        } catch (SQLException e) {
        	sendError(response, 500, "Error during the conclusion of the project: " );
        }
    }

}