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

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.Task;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.TaskAssignmentDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.WorkedHoursDAO;

@WebServlet("/salva-ore")
public class SalvaOre extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || !PersonType.COLLABORATOR.equals(session.getAttribute("role"))) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        User collaboratore = (User) session.getAttribute("user");
        int projId  = parseIntOrDefault(request.getParameter("projId"),  0);
        int wpId    = parseIntOrDefault(request.getParameter("wpId"),    0);
        int taskId  = parseIntOrDefault(request.getParameter("taskId"),  0);
        int mese    = parseIntOrDefault(request.getParameter("mese"),    0);
        int ore     = parseIntOrDefault(request.getParameter("ore"),    -1);

        Map<String,String> formData = new HashMap<>();
        formData.put("projId",  String.valueOf(projId));
        formData.put("wpId",    String.valueOf(wpId));
        formData.put("taskId",  String.valueOf(taskId));

        if (taskId == 0 || mese == 0 || ore < 0 || ore > 744) {
            fail(request, response, "Invalid data: hours must be between 0 and 744.", formData);
            return;
        }

        try {
            TaskAssignmentDAO taDAO = new TaskAssignmentDAO(connection);
            if (!taDAO.isAssignedAndProjectAssigned(taskId, collaboratore.getId())) {
                fail(request, response, "Operation not allowed.", formData);
                return;
            }

            TaskDAO taskDAO = new TaskDAO(connection);
            Task task = taskDAO.findById(taskId);
            if (task == null || mese < task.getStartMonth() || mese > task.getEndMonth()) {
                fail(request, response, "Invalid month for this task.", formData);
                return;
            }

            new WorkedHoursDAO(connection).save(taskId, collaboratore.getId(), mese, ore);

            String msg = URLEncoder.encode("Hours for month " + mese + " saved!", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() +
                "/homeCollaboratore?projId=" + projId + "&wpId=" + wpId +
                "&taskId=" + taskId + "&successMsg=" + msg);

        } catch (SQLException e) {
            throw new ServletException("Error saving worked hours", e);
        }
    }

    private void fail(HttpServletRequest request, HttpServletResponse response,
                      String errorMsg, Map<String,String> formData)
            throws ServletException, IOException {
        request.setAttribute("errorMsg", errorMsg);
        request.setAttribute("formData", formData);
        request.getRequestDispatcher("/homeCollaboratore").forward(request, response);
    }
}