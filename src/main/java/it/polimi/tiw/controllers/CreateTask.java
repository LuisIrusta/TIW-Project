package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.WorkPackageDAO;


@WebServlet("/create-task")
public class CreateTask extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User admin = (User) session.getAttribute("user");

        String wpStr = request.getParameter("wpId");
        String title = request.getParameter("title");
        String description = request.getParameter("description");
        String startMonthStr = request.getParameter("startMonth");
        String endMonthStr = request.getParameter("endMonth");

        Map<String, String> formData = new HashMap<>();
        formData.put("t_wpId", wpStr);
        formData.put("t_title", title);
        formData.put("t_description", description);
        formData.put("t_startMonth", startMonthStr);
        formData.put("t_endMonth", endMonthStr);

        try {
            int wpId;
            try {
                wpId = Integer.parseInt(wpStr.trim());
            } catch (NumberFormatException | NullPointerException e) {
                fail(request, response, "You must select a Work Package", formData);
                return;
            }
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            WorkPackage wp = wpDAO.findById(wpId);
            if (wp == null) {
                fail(request, response, "Invalid Work Package", formData);
                return;
            }
            ProjectDAO projectDAO = new ProjectDAO(connection);
            Project progetto = projectDAO.findById(wp.getProjectId());
            if (progetto == null || progetto.getAdministratorId() != admin.getId() || !progetto.isCreated()) {
                fail(request, response, "The selected WP does not belong to any of your projects in CREATED state", formData);
                return;
            }
            if (title == null || title.isBlank()) {
                fail(request, response, "The title of the task is mandatory", formData);
                return;
            }
            int startMonth, endMonth;
            try {
                startMonth = Integer.parseInt(startMonthStr.trim());
                endMonth = Integer.parseInt(endMonthStr.trim());
            } catch (NumberFormatException | NullPointerException e) {
                fail(request, response, "Months should be integer numbers", formData);
                return;
            }
            if (startMonth < 1 || endMonth < startMonth) {
                fail(request, response, "Invalid month interval (start >= 1 end >= start).", formData);
                return;
            }
            if (startMonth < wp.getStartMonth() || endMonth > wp.getEndMonth()) {
                fail(request, response, "The months of the task should be contained within the interval of the Work Package", formData);
                return;
            }

            TaskDAO taskDAO = new TaskDAO(connection);
            String desc = (description == null) ? "" : description.trim();
            taskDAO.createTask(wpId, title.trim(), desc, startMonth, endMonth);

            String msg = URLEncoder.encode("Task created successfully", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/home-admin?successMsg=" + msg);

        } catch (SQLException e) {
            throw new ServletException("Error while creating the task", e);
        }
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, String errorMsg, Map<String, String> formData) throws ServletException, IOException {
        request.setAttribute("errorMsg", errorMsg);
        request.setAttribute("formData", formData);
        request.getRequestDispatcher("/home-admin").forward(request, response);
    }
}