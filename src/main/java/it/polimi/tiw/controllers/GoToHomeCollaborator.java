package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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


@WebServlet("/api/home-collaborator")
public class GoToHomeCollaborator extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendJsonResponse(response, 401, Map.of("error", "Sessione scaduta o non valida. Effettua il login."));
            return;
        }
        User collaborator = (User) session.getAttribute("user");

        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            TaskDAO taskDAO = new TaskDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            List<Project> projects = projectDAO.findByCollaborator(collaborator.getId());

            int selectedProjectId = parseIntOrDefault(request.getParameter("projectId"), -1);
            Project selectedProject = null;
            for (Project p : projects) {
                if (p.getId() == selectedProjectId) { selectedProject = p; break; }
            }

            List<WorkPackage> wps = null;
            WorkPackage selectedWp = null;
            if (selectedProject != null) {
                wps = wpDAO.findByProjectAndCollaborator(selectedProject.getId(), collaborator.getId());
                int selectedWpId = parseIntOrDefault(request.getParameter("wpId"), -1);
                for (WorkPackage wp : wps) {
                    if (wp.getId() == selectedWpId) { selectedWp = wp; break; }
                }
            }

            List<Task> tasks = null;
            Task selectedTask = null;
            if (selectedWp != null) {
                tasks = taskDAO.findByWorkPackageAndCollaborator(selectedWp.getId(), collaborator.getId());
                int selectedTaskId = parseIntOrDefault(request.getParameter("taskId"), -1);
                for (Task t : tasks) {
                    if (t.getId() == selectedTaskId) { selectedTask = t; break; }
                }
            }

            Integer totPlanned = null;
            Integer totWorked = null;
            List<Integer> monthTask = null;
            if (selectedTask != null) {
                totPlanned = hoursDAO.getTotalPlannedForTask(selectedTask.getId());
                totWorked = hoursDAO.getTotalWorkedForCollaboratorTask(selectedTask.getId(), collaborator.getId());
                monthTask = new ArrayList<>();
                for (int m = selectedTask.getStartMonth(); m <= selectedTask.getEndMonth(); m++) {
                    monthTask.add(m);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("collaboratore",    collaborator);
            result.put("progetti",         projects);
            result.put("selectedProject",  selectedProject);
            result.put("wps",              wps);
            result.put("selectedWp",       selectedWp);
            result.put("tasks",            tasks);
            result.put("selectedTask",     selectedTask);
            result.put("totPreviste",      totPlanned);
            result.put("totLavorate",      totWorked);
            result.put("mesiTask",         monthTask);

            sendJsonResponse(response, 200, result);

        } catch (SQLException e) {
            throw new ServletException("Errore nel caricamento della HOME COLLABORATORE", e);
        }
    }
}