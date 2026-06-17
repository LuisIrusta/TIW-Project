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

@WebServlet("/dev-login-manager")
public class DevLoginAsManager extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            UserDAO userDAO = new UserDAO(connection);

            List<User> tecnicos = userDAO.findAllTechnicals();
            if (tecnicos.isEmpty()) {
                throw new ServletException("No hay tecnicos en la BD");
            }
            User responsable = tecnicos.get(0);

            HttpSession session = req.getSession(true);
            session.setAttribute("user", responsable);
            session.setAttribute("role", "RESPONSABLE");   //or the enum Role.RESPONSABLE
            res.sendRedirect(req.getContextPath() + "/home-manager");
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }
}