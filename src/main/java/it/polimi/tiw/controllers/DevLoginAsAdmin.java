package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.UserDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

//TODO Delete this class after log-in is done. This is just a bypass class

@WebServlet("/dev-login-admin")
public class DevLoginAsAdmin extends AbstractServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        try {
            User admin = new UserDAO(connection).checkCredentials("admin1", "admin1");
            if (admin == null) {
                throw new ServletException("admin1 does not exist; revise the DB");
            }
            HttpSession session = req.getSession(true);
            session.setAttribute("user", admin);
            session.setAttribute("role", PersonType.ADMINISTRATIVE);
            res.sendRedirect(req.getContextPath() + "/home-admin");
        } catch (SQLException e) {
            throw new ServletException(e);
        }
    }
}