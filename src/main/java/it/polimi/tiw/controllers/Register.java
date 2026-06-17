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

import org.thymeleaf.context.WebContext;

import it.polimi.tiw.beans.*;
import it.polimi.tiw.dao.UserDAO;

@WebServlet("/register")
public class Register extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        WebContext ctx = getWebContext(request, response);
        ctx.setVariable("formData", new HashMap<>());  // ← aggiungi questa riga
        templateEngine.process("register", ctx, response.getWriter());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        String firstName = request.getParameter("first_name");
        String lastName  = request.getParameter("last_name");
        String username  = request.getParameter("username");
        String password  = request.getParameter("password");
        String conferma  = request.getParameter("conferma");
     // Recuperiamo il valore inviato dal form (es. "ADMIN", "TECHNICAL", o "COLLABORATOR")
        String roleParam = request.getParameter("role");
        PersonType userRole = null ;
        if (roleParam != null) {
            try {
                // Converte la stringa direttamente nell'enum corrispondente (case-sensitive)
                userRole = PersonType.valueOf(roleParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Gestisci il caso in cui il parametro non corrisponda a nessun ruolo valido
                userRole = null; 
            }
        }

        // Ora hai la tua singola variabile 'userRole' pronta all'uso!

 

        Map<String, String> formData = new HashMap<>();
        formData.put("first_name", firstName);
        formData.put("last_name",  lastName);
        formData.put("username",   username);

        if (firstName == null || firstName.isBlank() ||
            lastName  == null || lastName.isBlank()  ||
            username  == null || username.isBlank()  ||
            password  == null || password.isBlank()  ) {
            fail(request, response, "Please fill in all mandatory fields.", formData);
            return;
        }
        if (!password.equals(conferma)) {
            fail(request, response, "Passwords do not match.", formData);
            return;
        }
        if (  userRole == null ) {
            fail(request, response, "Seleziona almeno un ruolo.", formData);
            return;
        }

        try {
            UserDAO userDAO = new UserDAO(connection);

            if (userDAO.findByUsername(username.trim())!=null) {
                fail(request, response, "Username already in use.", formData);
                return;
            }

            String hash = password;
            userDAO.createUser(username, hash, firstName,
            		lastName, userRole);

            String msg = URLEncoder.encode("Account created! You can now log in.", StandardCharsets.UTF_8);
            response.sendRedirect(request.getContextPath() + "/login?successMsg=" + msg);

        } catch (SQLException e) {
            throw new ServletException("Error during registration", e);
        }
    }

    private void fail(HttpServletRequest request, HttpServletResponse response,
                      String errorMsg, Map<String, String> formData)
            throws ServletException, IOException {
        WebContext ctx = getWebContext(request, response);
        ctx.setVariable("errorMsg", errorMsg);
        ctx.setVariable("formData", formData);
        templateEngine.process("register", ctx, response.getWriter());
    }
}