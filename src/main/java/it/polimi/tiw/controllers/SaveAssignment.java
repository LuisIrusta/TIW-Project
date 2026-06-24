package it.polimi.tiw.controllers;

import java.io.IOException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.dao.WorkPackageDAO;


@WebServlet("/api/save-assignment")
public class SaveAssignment extends AbstractServlet {

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
        Integer taskId = null;
        if (jsonRequest.has("taskId") && !jsonRequest.get("taskId").isJsonNull()) {
            try {
                taskId = jsonRequest.get("taskId").getAsInt();
            } catch (Exception e) {
                sendError(response, 400, "L'identificativo 'taskId' deve essere un numero intero.");
                return;
            }
        }
        try {
            TaskDAO taskDAO = new TaskDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            ProjectDAO projectDAO = new ProjectDAO(connection);
            UserDAO userDAO = new UserDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            Task task = taskDAO.findById(taskId);
            if (task == null) {
            	sendError(response,404,"Nonexistent task");
                return;
            }
            WorkPackage wp = wpDAO.findById(task.getWpId());
            Project project = projectDAO.findById(wp.getProjectId());
            if (project.getManagerId() != manager.getId()) {
            	sendError(response,403,"You are not the manager of this project");
                return;
            }
            if (!project.isCreated()) {
                fail( response, project.getId(), wp.getId(), task.getId(),
                        "The project is no longer in CREATED state: assignations are non modifiable", null, null);
                return;
            }
            List<Integer> collabIds = new ArrayList<>();
            Set<Integer> submittedColls = new HashSet<>();
            if (jsonRequest.has("collaborators") && jsonRequest.get("collaborators").isJsonArray()) {
                for (com.google.gson.JsonElement el : jsonRequest.getAsJsonArray("collaborators")) {
                    Integer cid;
                    try {
                        cid = el.getAsInt();
                    } catch (Exception e) {
                        sendError(response, 400, "Collaborator not valid");
                        return;
                    }
                    submittedColls.add(cid);
                    if (cid.intValue() == manager.getId()) {
                        fail(response, project.getId(), wp.getId(), task.getId(),
                                "You cannot assign yourself as a collaborator",
                                readSubmittedHours(jsonRequest, task), submittedColls);
                        return;
                    }
                    User c = userDAO.findById(cid);
                    if (c == null || !c.isTechnical() && !c.isCollaborator()) {
                        fail(response, project.getId(), wp.getId(), task.getId(),
                                "One of the selected collaborators is not valid",
                                readSubmittedHours(jsonRequest, task), submittedColls);
                        return;
                    }
                    collabIds.add(cid);
                }
            }   
            
            Map<Integer, String> submittedHours = readSubmittedHours(jsonRequest, task);
            Map<Integer, Integer> hoursByMonth = new HashMap<>();
            for (int m = task.getStartMonth(); m <= task.getEndMonth(); m++) {
                String raw = submittedHours.get(m);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                int ore;
                try {
                    ore = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    fail( response, project.getId(), wp.getId(), task.getId(),
                            "The hours of the month M " + m + " should be an integer number", submittedHours, submittedColls);
                    return;
                }
                if (ore < 0) {
                    fail( response, project.getId(), wp.getId(), task.getId(),
                            "Hours cannot be negative (month M" + m + ").", submittedHours, submittedColls);
                    return;
                }
                hoursByMonth.put(m, ore);
            }
            hoursDAO.saveTaskAssignment(task.getId(), collabIds, hoursByMonth);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Task operazione completata con successo!");
            result.put("projectId", project.getId());
            result.put("wpId", wp.getId());
            result.put("taskId", task.getId());

            sendJsonResponse(response, 200, result);
            return; 

        } catch (SQLException e) {
            throw new ServletException("Error while saving the assignation", e);
        }
    }


    
    private Map<Integer, String> readSubmittedHours(JsonObject jsonRequest, Task task) {
        Map<Integer, String> map = new HashMap<>();
        
        // Controlliamo se c'è un sotto-oggetto "hours" (es: "hours": {"1": 10, "2": "15"})
        JsonObject hoursObj = null;
        if (jsonRequest.has("hours") && jsonRequest.get("hours").isJsonObject()) {
            hoursObj = jsonRequest.getAsJsonObject("hours");
        }
        
        for (int m = task.getStartMonth(); m <= task.getEndMonth(); m++) {
            String key = String.valueOf(m);
            if (hoursObj != null && hoursObj.has(key) && !hoursObj.get(key).isJsonNull()) {
                map.put(m, hoursObj.get(key).getAsString());
            } else {
                map.put(m, "");
            }
        }
        return map;
    }

    private void fail(HttpServletResponse response, int projectId, int wpId, int taskId, 
            String errorMsg, Map<Integer, String> submittedHours, Set<Integer> submittedColls) 
            throws IOException {
    			Map<String, Object> errorDetails = new HashMap<>();
    			errorDetails.put("success", false);
    			errorDetails.put("error", errorMsg);
    			errorDetails.put("projectId", projectId);
    			errorDetails.put("wpId", wpId);
    			errorDetails.put("taskId", taskId);

    			if (submittedHours != null) {
    				errorDetails.put("submittedHours", submittedHours);
    				errorDetails.put("submittedCollaborators", submittedColls != null ? submittedColls : new HashSet<Integer>());
    			} else {
    				errorDetails.put("submittedHours", new HashMap<Integer, String>());
    				errorDetails.put("submittedCollaborators", new HashSet<Integer>());
    			}

    			sendJsonResponse(response, 400, errorDetails);
    		}
}