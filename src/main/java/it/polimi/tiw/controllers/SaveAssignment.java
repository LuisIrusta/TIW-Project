package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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


@WebServlet("/save-assignment")
public class SaveAssignment extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User manager = (User) session.getAttribute("user");
        
        Integer taskId = tryParse(request.getParameter("taskId"));
        if (taskId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Task not specified");
            return;
        }
        try {
            TaskDAO taskDAO = new TaskDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            ProjectDAO projectDAO = new ProjectDAO(connection);
            UserDAO userDAO = new UserDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            Task task = taskDAO.findById(taskId);
            if (task == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Nonexistent task");
                return;
            }
            WorkPackage wp = wpDAO.findById(task.getWpId());
            Project project = projectDAO.findById(wp.getProjectId());
            if (project.getManagerId() != manager.getId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "You are not the manager of this project");
                return;
            }
            if (!project.isCreated()) {
                fail(request, response, project.getId(), wp.getId(), task.getId(),
                        "The project is no longer in CREATED state: assignations are non modifiable", null, null);
                return;
            }
            String[] collParams = request.getParameterValues("collaborators");
            List<Integer> collabIds = new ArrayList<>();
            Set<Integer> submittedColls = new HashSet<>();
            if (collParams != null) {
                for (String cp : collParams) {
                    Integer cid = tryParse(cp);
                    if (cid == null) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                                "Collaborator not valid");
                        return;
                    }
                    submittedColls.add(cid);
                    if (cid.intValue() == manager.getId()) {
                        fail(request, response, project.getId(), wp.getId(), task.getId(),
                                "You cannot assign yourself as a collaborator",
                                readSubmittedHours(request, task), submittedColls);
                        return;
                    }
                    User c = userDAO.findById(cid);
                    if (c == null || !c.isTechnical() && !c.isCollaborator()) {
                        fail(request, response, project.getId(), wp.getId(), task.getId(),
                                "One of the selected collaborators is not valid",
                                readSubmittedHours(request, task), submittedColls);
                        return;
                    }
                    collabIds.add(cid);
                }
            }
            Map<Integer, String> submittedHours = readSubmittedHours(request, task);
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
                    fail(request, response, project.getId(), wp.getId(), task.getId(),
                            "The hours of the month M " + m + " should be an integer number", submittedHours, submittedColls);
                    return;
                }
                if (ore < 0) {
                    fail(request, response, project.getId(), wp.getId(), task.getId(),
                            "Hours cannot be negative (month M" + m + ").", submittedHours, submittedColls);
                    return;
                }
                hoursByMonth.put(m, ore);
            }
            hoursDAO.saveTaskAssignment(task.getId(), collabIds, hoursByMonth);

            String msg = URLEncoder.encode("Task assignation saved", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/home-manager"
                    + "?projectId=" + project.getId()
                    + "&wpId=" + wp.getId()
                    + "&taskId=" + task.getId()
                    + "&successMsg=" + msg);

        } catch (SQLException e) {
            throw new ServletException("Error while saving the assignation", e);
        }
    }


    
    private Map<Integer, String> readSubmittedHours(HttpServletRequest request, Task task) {
        Map<Integer, String> map = new HashMap<>();
        for (int m = task.getStartMonth(); m <= task.getEndMonth(); m++) {
            String raw = request.getParameter("hours" + m);
            map.put(m, raw == null ? "" : raw);
        }
        return map;
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, int projectId, int wpId, int taskId, String errorMsg, Map<Integer, String> submittedHours, Set<Integer> submittedColls)
            throws ServletException, IOException {
        request.setAttribute("errorMsg", errorMsg);
        if (submittedHours != null) {
            request.setAttribute("submittedHours", submittedHours);
            request.setAttribute("submittedCollaborators",
                    submittedColls != null ? submittedColls : new HashSet<Integer>());
        }
        request.getRequestDispatcher("/home-manager").forward(request, response);
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