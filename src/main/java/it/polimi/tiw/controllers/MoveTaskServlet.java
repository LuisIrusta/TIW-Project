package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.Task;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.WorkPackageDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/move-task")
public class MoveTaskServlet extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
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

        int taskId         = parseIntOrDefault(request.getParameter("taskId"), 0);
        int targetWpId     = parseIntOrDefault(request.getParameter("targetWpId"), 0);
        int targetPosition = parseIntOrDefault(request.getParameter("targetPosition"), 0);

        if (taskId == 0 || targetWpId == 0 || targetPosition < 1) {
            sendError(response, 400, "Missing or invalid parameters.");
            return;
        }

        try {
            TaskDAO taskDAO = new TaskDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            ProjectDAO projectDAO = new ProjectDAO(connection);

            Task task = taskDAO.findById(taskId);
            if (task == null) {
                sendError(response, 404, "Task not found.");
                return;
            }

            WorkPackage sourceWp = wpDAO.findById(task.getWpId());
            WorkPackage targetWp = wpDAO.findById(targetWpId);
            if (sourceWp == null || targetWp == null) {
                sendError(response, 404, "Work Package not found.");
                return;
            }
            if (sourceWp.getId() != targetWp.getId()) {
                TaskDAO taskDAO2 = new TaskDAO(connection);
                List<Task> sourceTasks = taskDAO2.findByWorkPackage(sourceWp.getId());
                if (sourceTasks.size() <= 1) {
                    sendError(response, 400, "Cannot move the only task of a Work Package.");
                    return;
                }
            }
            // Entrambi i WP devono appartenere allo stesso progetto e quel progetto
            // deve essere ancora modificabile (stato CREATED)
            if (sourceWp.getProjectId() != targetWp.getProjectId()) {
                sendError(response, 400, "Source and target Work Package must belong to the same project.");
                return;
            }
            Project project = projectDAO.findById(sourceWp.getProjectId());
            if (project == null || !project.isCreated()) {
                sendError(response, 403, "Project is not modifiable.");
                return;
            }

            // Controllo di congruenza: il task deve rientrare nei mesi del WP destinazione
            if (task.getStartMonth() < targetWp.getStartMonth() ||
                task.getEndMonth()   > targetWp.getEndMonth()) {
                sendError(response, 400,
                    "Move rejected: task months (" + task.getStartMonth() + "-" + task.getEndMonth() +
                    ") are not compatible with the target Work Package (" +
                    targetWp.getStartMonth() + "-" + targetWp.getEndMonth() + ").");
                return;
            }

            connection.setAutoCommit(false);
            connection.setAutoCommit(false);
            try {
                taskDAO.moveTask(taskId, targetWpId, targetPosition);
                connection.commit();
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                sendJsonResponse(response, 200, result);
            } catch (SQLException e) {
                connection.rollback();
                sendError(response, 500, "Errore durante lo spostamento del task: " + e.getMessage());
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            throw new ServletException("Error moving task", e);
        }
    }
}