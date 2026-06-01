package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.PlannedHoursDAO;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskAssignmentDAO;

@WebServlet("/salva-task")
public class SalvaTask extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || !PersonType.TECHNICAL.equals(session.getAttribute("role"))) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        User manager   = (User) session.getAttribute("user");
        int projId     = parseIntOrDefault(request.getParameter("projId"),  0);
        int wpId       = parseIntOrDefault(request.getParameter("wpId"),    0);
        int taskId     = parseIntOrDefault(request.getParameter("taskId"),  0);
        String[] mesi      = request.getParameterValues("mesi[]");
        String[] ore       = request.getParameterValues("ore[]");
        String[] collabIds = request.getParameterValues("collaboratori");

        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            Project progetto = projectDAO.findByIdAndManager(projId, manager.getId());

            if (progetto == null || !progetto.isCreated()) {
                fail(request, response, "Project not modifiable.", projId, wpId, taskId);
                return;
            }

            connection.setAutoCommit(false);
            try {
                if (mesi != null && ore != null) {
                    Map<Integer,Integer> oreMap = new LinkedHashMap<>();
                    for (int i = 0; i < mesi.length; i++)
                        oreMap.put(Integer.parseInt(mesi[i]), Math.max(0, Integer.parseInt(ore[i])));
                    new PlannedHoursDAO(connection).saveAll(taskId, oreMap);
                }
                if (collabIds != null) {
                    TaskAssignmentDAO taDAO = new TaskAssignmentDAO(connection);
                    for (String cid : collabIds)
                        taDAO.assign(taskId, Integer.parseInt(cid));
                }
                connection.commit();
                String msg = URLEncoder.encode("Data saved successfully!", StandardCharsets.UTF_8);
                response.sendRedirect(request.getContextPath() +
                    "/homeResponsabile?projId=" + projId + "&wpId=" + wpId + "&taskId=" + taskId + "&successMsg=" + msg);

            } catch (SQLException e) {
                connection.rollback();
                fail(request, response, extractTriggerMessage(e), projId, wpId, taskId);
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            throw new ServletException("Error saving task data", e);
        }
    }

    private void fail(HttpServletRequest request, HttpServletResponse response,
                      String errorMsg, int projId, int wpId, int taskId)
            throws ServletException, IOException {
        request.setAttribute("errorMsg", errorMsg);
        request.getRequestDispatcher("/homeResponsabile?projId=" + projId +
            "&wpId=" + wpId + "&taskId=" + taskId).forward(request, response);
    }
}