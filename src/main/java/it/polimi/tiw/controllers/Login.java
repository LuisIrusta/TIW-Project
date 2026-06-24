package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;

import org.thymeleaf.context.WebContext;

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.UserDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
@WebServlet("/login")
public class Login extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        WebContext ctx = getWebContext(request, response);
        templateEngine.process("login", ctx, response.getWriter());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String tipoStr  = request.getParameter("tipo");

        if (username == null || username.isBlank() ||
            password == null || password.isBlank() ||
            tipoStr  == null || tipoStr.isBlank()) {
            fail(request, response, "Please fill in all fields and select a role.");
            return;
        }

        try {
            UserDAO userDAO = new UserDAO(connection);
            User user = userDAO.checkCredentials(username, password);

            if (user == null ) {
                fail(request, response, "Invalid username or password.");
                return;
            }

            // Controlla che il ruolo selezionato sia abilitato
            PersonType tipo= null;
            boolean autorizzato= false;
            switch (tipoStr) {
                case "TECHNICAL" -> { tipo = PersonType.TECHNICAL;       autorizzato = user.isTechnical(); }
                case "COLLABORATOR" -> { tipo = PersonType.COLLABORATOR;    autorizzato = user.isCollaborator(); }
                case "ADMINISTRATIVE"->{ tipo = PersonType.ADMINISTRATIVE;  autorizzato = user.isAdministrative(); }
                default             -> { fail(request, response, "Invalid role selected."); return; }
            }

            if (!autorizzato) {
                fail(request, response, "You are not enabled to access as " + tipoStr + ".");
                return;
            }

            HttpSession session = request.getSession(true);
            session.setAttribute("user", user);
            session.setAttribute("administrative", user.isAdministrative());
            session.setAttribute("technical", user.isTechnical());
            session.setAttribute("collaborator", user.isCollaborator());
            session.setAttribute("role", tipo);
            String redirect = switch (tipoStr) {
                case "TECHNICAL"    -> "/home-manager";
                case "COLLABORATOR"    -> "/home-collaborator";
                case "ADMINISTRATIVE"  -> "/home-admin";
                default                -> "/login";
            };
            response.sendRedirect(request.getContextPath() + redirect);

        } catch (SQLException e) {
            throw new ServletException("Error during login", e);
        }
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, String errorMsg)
            throws ServletException, IOException {
        WebContext ctx = getWebContext(request, response);
        ctx.setVariable("errorMsg", errorMsg);
        templateEngine.process("login", ctx, response.getWriter());
    }
}