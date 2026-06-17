package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

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


@WebServlet("/save-worked-hours")
public class SaveWorkedHours extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User collaborator = (User) session.getAttribute("user");

        Integer taskId = tryParse(request.getParameter("task_id"));
        if (taskId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Task not specified");
            return;
        }
        String monthStr = request.getParameter("month_index");
        String hourStr  = request.getParameter("hours");
        try {
            TaskDAO taskDAO = new TaskDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            ProjectDAO projectDAO = new ProjectDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            Task task = taskDAO.findById(taskId);
            if (task == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Nonexistent task");
                return;
            }
            WorkPackage wp = wpDAO.findById(task.getWpId());
            Project project = projectDAO.findById(wp.getProjectId());
            if (!hoursDAO.getCollaboratorIdsOfTask(task.getId()).contains(collaborator.getId())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "You are not assigned to this task");
                return;
            }
            if (!project.isAssigned()) {
                fail(request, response, project.getId(), wp.getId(), task.getId(),
                        "Hours can only be assigned to projects in ASSIGNED state", monthStr, hourStr);
                return;
            }
            Integer month = tryParse(monthStr);
            if (month == null || month < task.getStartMonth() || month > task.getEndMonth()) {
                fail(request, response, project.getId(), wp.getId(), task.getId(),
                        "Invalid month: it should range from M" + task.getStartMonth()
                        + " to M" + task.getEndMonth() + ".", monthStr, hourStr);
                return;
            }
            Integer hours = tryParse(hourStr);
            if (hours == null) {
                fail(request, response, project.getId(), wp.getId(), task.getId(),
                        "Hours should be an integer number", monthStr, hourStr);
                return;
            }
            if (hours < 0) {
                fail(request, response, project.getId(), wp.getId(), task.getId(),
                        "Hours cannot be negative numbers", monthStr, hourStr);
                return;
            }

            hoursDAO.saveWorkedHours(task.getId(), collaborator.getId(), month, hours);

            String msg = URLEncoder.encode("Ore lavorate salvate.", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/home-collaborator"
                    + "?projectId=" + project.getId()
                    + "&wpId=" + wp.getId()
                    + "&taskId=" + task.getId()
                    + "&successMsg=" + msg);

        } catch (SQLException e) {
            throw new ServletException("Error while saving the worked hours", e);
        }
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, int projectId, int wpId, int taskId, String errorMsg, String monthStr, String hourStr) throws ServletException, IOException {
        request.setAttribute("errorMsg", errorMsg);
        request.setAttribute("submittedMese", monthStr);
        request.setAttribute("submittedOre", hourStr);

        request.getRequestDispatcher("/home-collaborator").forward(request, response);
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