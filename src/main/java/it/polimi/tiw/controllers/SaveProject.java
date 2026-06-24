package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.dao.WorkPackageDAO;

@WebServlet("/api/save-project")
public class SaveProject extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 1. Auth check
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendJsonResponse(response, 401, Map.of("error", "Sessione scaduta o non valida. Effettua il login."));
            return;
        }
        User admin = (User) session.getAttribute("user");
        if (!admin.isAdministrative()) { 
            sendJsonResponse(response, 403, Map.of("error", "Accesso negato. Non hai i permessi necessari."));
            return;
        }
        // 2. Parse JSON body
        JsonObject body = readJsonRequest(request);
        if (body == null) {
            sendJsonResponse(response, 400, Map.of("success", false, "error", "Payload JSON mancante o malformato."));
            return;
        }

        // 3. Extract and validate project fields
        String title        = body.has("title")         ? body.get("title").getAsString()         : null;
        String durationStr  = body.has("durationMonth") ? body.get("durationMonth").getAsString() : null;
        String managerIdStr = body.has("managerId")     ? body.get("managerId").getAsString()     : null;

        if (title == null || title.isBlank()) {
            sendError(response, 400, "The title of the project is mandatory");
            return;
        }

        int durMonth;
        try {
            durMonth = Integer.parseInt(durationStr.trim());
        } catch (NumberFormatException | NullPointerException e) {
            sendError(response, 400, "The duration must be an integer number");
            return;
        }
        if (durMonth <= 0) {
            sendError(response, 400, "Duration must be greater than zero");
            return;
        }

        int manId;
        try {
            manId = Integer.parseInt(managerIdStr.trim());
        } catch (NumberFormatException | NullPointerException e) {
            sendError(response, 400, "You must select a manager");
            return;
        }

        // 4. Validate WP array exists and is non-empty
        if (!body.has("workPackages") || !body.get("workPackages").isJsonArray()) {
            sendError(response, 400, "A project must have at least one Work Package");
            return;
        }
        JsonArray wps = body.getAsJsonArray("workPackages");
        if (wps.isEmpty()) {
            sendError(response, 400, "A project must have at least one Work Package");
            return;
        }

        // 5. Validate every WP and its tasks before touching the DB
        for (int i = 0; i < wps.size(); i++) {
            JsonObject wp = wps.get(i).getAsJsonObject();

            String wpTitle      = wp.has("title")      ? wp.get("title").getAsString()      : null;
            String wpStartStr   = wp.has("startMonth") ? wp.get("startMonth").getAsString() : null;
            String wpEndStr     = wp.has("endMonth")   ? wp.get("endMonth").getAsString()   : null;

            if (wpTitle == null || wpTitle.isBlank()) {
                sendError(response, 400, "WP " + (i + 1) + ": title is mandatory");
                return;
            }

            int wpStart, wpEnd;
            try {
                wpStart = Integer.parseInt(wpStartStr.trim());
                wpEnd   = Integer.parseInt(wpEndStr.trim());
            } catch (NumberFormatException | NullPointerException e) {
                sendError(response, 400, "WP " + (i + 1) + ": months must be integer numbers");
                return;
            }
            if (wpStart < 1 || wpEnd < wpStart) {
                sendError(response, 400, "WP " + (i + 1) + ": invalid month interval (start >= 1, end >= start)");
                return;
            }
            if (wpStart > durMonth || wpEnd > durMonth) {
                sendError(response, 400, "WP " + (i + 1) + ": months must be within the project duration");
                return;
            }

            if (!wp.has("tasks") || !wp.get("tasks").isJsonArray()) {
                sendError(response, 400, "WP " + (i + 1) + " must have at least one task");
                return;
            }
            JsonArray tasks = wp.getAsJsonArray("tasks");
            if (tasks.isEmpty()) {
                sendError(response, 400, "WP " + (i + 1) + " must have at least one task");
                return;
            }

            for (int j = 0; j < tasks.size(); j++) {
                JsonObject task = tasks.get(j).getAsJsonObject();

                String taskTitle    = task.has("title")       ? task.get("title").getAsString()       : null;
                String taskStartStr = task.has("startMonth")  ? task.get("startMonth").getAsString()  : null;
                String taskEndStr   = task.has("endMonth")    ? task.get("endMonth").getAsString()    : null;

                if (taskTitle == null || taskTitle.isBlank()) {
                    sendError(response, 400, "WP " + (i + 1) + ", Task " + (j + 1) + ": title is mandatory");
                    return;
                }

                int taskStart, taskEnd;
                try {
                    taskStart = Integer.parseInt(taskStartStr.trim());
                    taskEnd   = Integer.parseInt(taskEndStr.trim());
                } catch (NumberFormatException | NullPointerException e) {
                    sendError(response, 400, "WP " + (i + 1) + ", Task " + (j + 1) + ": months must be integer numbers");
                    return;
                }
                if (taskStart < 1 || taskEnd < taskStart) {
                    sendError(response, 400, "WP " + (i + 1) + ", Task " + (j + 1) + ": invalid month interval");
                    return;
                }
                if (taskStart < wpStart || taskEnd > wpEnd) {
                    sendError(response, 400, "WP " + (i + 1) + ", Task " + (j + 1) + ": months must be within the WP interval");
                    return;
                }
            }
        }

        // 6. All validation passed — persist inside a single transaction
        try {
            UserDAO userDAO = new UserDAO(connection);
            User manager = userDAO.findById(manId);
            if (manager == null || !manager.isTechnical()) {
                sendError(response, 400, "The selected manager is not valid");
                return;
            }

            connection.setAutoCommit(false);
            try {
                ProjectDAO projectDAO   = new ProjectDAO(connection);
                WorkPackageDAO wpDAO    = new WorkPackageDAO(connection);
                TaskDAO taskDAO         = new TaskDAO(connection);

                // Insert project and get its generated id
                int projectId = projectDAO.createProject(title.trim(), durMonth, admin.getId(), manId);

                // Insert WPs and tasks in order (order index = insertion order)
                for (int i = 0; i < wps.size(); i++) {
                    JsonObject wp = wps.get(i).getAsJsonObject();

                    String wpTitle  = wp.get("title").getAsString().trim();
                    int    wpStart  = Integer.parseInt(wp.get("startMonth").getAsString().trim());
                    int    wpEnd    = Integer.parseInt(wp.get("endMonth").getAsString().trim());

                    // Pass the 1-based order index so the DAO can store it
                    int wpId = wpDAO.createWorkPackage(projectId, wpTitle, wpStart, wpEnd);

                    JsonArray tasks = wp.getAsJsonArray("tasks");
                    for (int j = 0; j < tasks.size(); j++) {
                        JsonObject task = tasks.get(j).getAsJsonObject();

                        String taskTitle = task.get("title").getAsString().trim();
                        String taskDesc  = task.has("description") && !task.get("description").isJsonNull()
                                           ? task.get("description").getAsString().trim()
                                           : "";
                        int taskStart = Integer.parseInt(task.get("startMonth").getAsString().trim());
                        int taskEnd   = Integer.parseInt(task.get("endMonth").getAsString().trim());

                        // Pass the 1-based order index
                        taskDAO.createTask(wpId, taskTitle, taskDesc, taskStart, taskEnd);
                    }
                }

                connection.commit();
                sendJsonResponse(response, 200, Map.of("success", true, "message", "Project saved successfully"));

            } catch (SQLException e) {
                connection.rollback();
                sendJsonResponse(response, 500, Map.of("success", false, "error", "Database error while saving the project"));
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            sendJsonResponse(response, 500, Map.of("success", false, "error", "Database connection error"));
        }
    }
}