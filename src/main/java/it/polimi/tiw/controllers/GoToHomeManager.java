package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.thymeleaf.context.WebContext;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.Task;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.HoursDAO;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.dao.WorkPackageDAO;


@WebServlet("/api/home-manager")
public class GoToHomeManager extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        process(request, response);
    }

    @SuppressWarnings("unchecked")
    private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

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
        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            TaskDAO taskDAO = new TaskDAO(connection);
            UserDAO userDAO = new UserDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            List<Project> projects = new ArrayList<>();
            for (Project p : projectDAO.findByManager(manager.getId())) {
                if (p.isCreated()) projects.add(p);
            }
            int selectedProjectId = parseIntOrDefault(request.getParameter("projectId"), -1);
            Project selectedProject = findProject(projects, selectedProjectId);
            List<WorkPackage> wps = null;
            WorkPackage selectedWp = null;
            if (selectedProject != null) {
                wps = wpDAO.findByProject(selectedProject.getId());
                int selectedWpId = parseIntOrDefault(request.getParameter("wpId"), -1);
                for (WorkPackage wp : wps) {
                    if (wp.getId() == selectedWpId) { selectedWp = wp; break; }
                }
            }
            List<Task> tasks = null;
            Task selectedTask = null;
            if (selectedWp != null) {
                tasks = taskDAO.findByWorkPackage(selectedWp.getId());
                int selectedTaskId = parseIntOrDefault(request.getParameter("taskId"), -1);
                for (Task t : tasks) {
                    if (t.getId() == selectedTaskId) { selectedTask = t; break; }
                }
            }
            List<Integer> monthTask = null;
            Map<Integer, String> plannedHours = null;
            List<User> collaborators = null;
            Set<Integer> assignedIds = null;
            if (selectedTask != null) {
                monthTask = new ArrayList<>();
                for (int m = selectedTask.getStartMonth(); m <= selectedTask.getEndMonth(); m++) {
                    monthTask.add(m);
                }
                collaborators = userDAO.findTechnicalsExcept(manager.getId());
                Object submittedHours = request.getAttribute("submittedHours");
                Object submittedColls = request.getAttribute("submittedCollaborators");
                if (submittedHours != null) {
                    plannedHours = (Map<Integer, String>) submittedHours;
                    assignedIds = (Set<Integer>) submittedColls;
                } else {
                    Map<Integer, Integer> dbHours = hoursDAO.getPlannedHoursMap(selectedTask.getId());
                    plannedHours = new HashMap<>();
                    for (Integer m : monthTask) {
                        plannedHours.put(m, dbHours.containsKey(m)
                                ? String.valueOf(dbHours.get(m)) : "");
                    }
                    assignedIds = hoursDAO.getCollaboratorIdsOfTask(selectedTask.getId());
                }
            }
            Map<String, Object> result = new HashMap<>();
            result.put("responsabile",      manager);
            result.put("progetti",         projects);
            result.put("collaboratori", collaborators);
            result.put("selectedProject",  selectedProject);
            result.put("wps",              wps);
            result.put("selectedWp",       selectedWp);
            result.put("tasks",            tasks);
            result.put("selectedTask",     selectedTask);
            result.put("orePreviste", plannedHours);
            result.put("assignedIds", assignedIds);
            result.put("mesiTask",         monthTask);
            sendJsonResponse(response, 200, result);
        } catch (SQLException e) {
            throw new ServletException("Error while loading HOME MANAGER", e);
        }
    }

    private Project findProject(List<Project> list, int id) {
        for (Project p : list) {
            if (p.getId() == id) return p;
        }
        return null;
    }
}