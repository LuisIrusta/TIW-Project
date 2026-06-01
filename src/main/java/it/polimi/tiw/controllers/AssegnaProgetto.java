package it.polimi.tiw.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.User;

@WebServlet("/assegna-progetto")
public class AssegnaProgetto extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || !PersonType.TECHNICAL.equals(session.getAttribute("role"))) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        User manager = (User) session.getAttribute("user");
        int projId   = parseIntOrDefault(request.getParameter("projId"), 0);

        try {
            CallableStatement cs = connection.prepareCall("{CALL sp_assign_project(?, ?)}");
            cs.setInt(1, projId);
            cs.setInt(2, manager.getId());
            cs.execute();
            String msg = URLEncoder.encode("Project assigned successfully!", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() +
                "/homeResponsabile?projId=" + projId + "&successMsg=" + msg);

        } catch (SQLException e) {
            request.setAttribute("errorMsg", extractTriggerMessage(e));
            request.getRequestDispatcher("/homeResponsabile?projId=" + projId)
                   .forward(request, response);
        }
    }
}