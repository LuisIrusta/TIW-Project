package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.UserDAO;

//TODO Delete this class after log-in is done. This is just a bypass class

@WebServlet("/dev-login-collaborator")
public class DevLoginAsCollaborator extends AbstractServlet {

    private static final long serialVersionUID = 1L;

	@Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        try {
            UserDAO userDAO = new UserDAO(connection);

            List<User> tecnicos = userDAO.findAllTechnicals();
            if (tecnicos.size() < 2) {
                throw new ServletException("Se necesitan al menos 2 tecnicos en la BD");
            }
            User collaborator = tecnicos.get(1);

            HttpSession session = req.getSession(true);
            session.setAttribute("user", collaborator);
            session.setAttribute("role", "COLLABORATOR");   // o tu enum Role.COLLABORATOR
            res.sendRedirect(req.getContextPath() + "/home-collaborator");
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }
}