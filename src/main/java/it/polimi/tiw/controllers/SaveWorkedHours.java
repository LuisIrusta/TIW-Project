package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;

import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.Task;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.HoursDAO;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.WorkPackageDAO;


@WebServlet("/api/save-worked-hours")
public class SaveWorkedHours extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
        	sendError(response, 401,  "Sessione scaduta. Effettua il login.");
            return;
        }
        User collaborator = (User) session.getAttribute("user");
        JsonObject jsonRequest = readJsonRequest(request);
        if (jsonRequest == null) {
            sendJsonResponse(response, 400, Map.of("success", false, "error", "Payload JSON mancante o malformato."));
            return;
        }
        Integer taskId = jsonRequest.has("taskId") && !jsonRequest.get("taskId").isJsonNull() 
                ? jsonRequest.get("taskId").getAsInt() : null;
        if (taskId == null) {
        	sendError(response, 400, "Task not specified");
            return;
        }
        String monthStr = jsonRequest.has("mese") && !jsonRequest.get("mese").isJsonNull() 
                ? jsonRequest.get("mese").getAsString() : null;
        String hourStr  = jsonRequest.has("ore") && !jsonRequest.get("ore").isJsonNull() 
                ? jsonRequest.get("ore").getAsString() : null;
        try {
            TaskDAO taskDAO = new TaskDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            ProjectDAO projectDAO = new ProjectDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            Task task = taskDAO.findById(taskId);
            if (task == null) {
            	sendError(response, 404, "Nonexistent task");
                return;
            }
            WorkPackage wp = wpDAO.findById(task.getWpId());
            Project project = projectDAO.findById(wp.getProjectId());
            if (!hoursDAO.getCollaboratorIdsOfTask(task.getId()).contains(collaborator.getId())) {
            	sendError(response, 403, "You are not assigned to this task");
                return;
            }
            if (!project.isAssigned()) {
            	sendError(response, 400,  "Hours can only be assigned to projects in ASSIGNED state");
                return;
            }
            Integer month = tryParse(monthStr);
            if (month == null || month < task.getStartMonth() || month > task.getEndMonth()) {
            	sendError(response, 400, "Invalid month: it should range from M" + task.getStartMonth()
                + " to M" + task.getEndMonth() + ".");
                return;
            }
            Integer hours = tryParse(hourStr);
            if (hours == null) {
            	sendError(response, 400, "Hours should be an integer number");
                return;
            }
            if (hours < 0) {
            	sendError(response, 400, "Hours cannot be negative numbers");
                return;
            }

            // Salvataggio ed esito positivo
            hoursDAO.saveWorkedHours(task.getId(), collaborator.getId(), month, hours);
            sendJsonResponse(response, 200, Map.of("success", true, "message", "Ore salvate!"));

        } catch (SQLException e) {
            sendJsonResponse(response, 500, Map.of("success", false, "error", "Errore del database."));
        }
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