package it.polimi.tiw.controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.UserDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
@WebServlet("/api/login")
public class Login extends AbstractServlet {

    private static final long serialVersionUID = 1L;


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        JsonObject jsonRequest = readJsonRequest(request);
        if (jsonRequest == null) {
            sendJsonResponse(response, 400, Map.of("success", false, "error", "Payload JSON mancante o malformato."));
            return;
        }


        String username  = jsonRequest.has("username")   ? jsonRequest.get("username").getAsString() : null;
        String password  = jsonRequest.has("password")   ? jsonRequest.get("password").getAsString() : null;
        String tipoStr  = jsonRequest.has("tipo")   ? jsonRequest.get("tipo").getAsString() : null;
        if (username == null || username.isBlank() ||
            password == null || password.isBlank() ||
            tipoStr  == null || tipoStr.isBlank()) {
        	sendError(response, 400, "Please fill in all fields and select a role.");
            return;
        }

        try {
            UserDAO userDAO = new UserDAO(connection);
            User user = userDAO.checkCredentials(username, password);

            if (user == null) {
            	sendError(response, 401, "Invalid username or password.");
            
                return;
            }

            // Controlla che il ruolo selezionato sia abilitato
            PersonType tipo= null;
            boolean autorizzato= false;
            switch (tipoStr) {
                case "TECHNICAL" -> { tipo = PersonType.TECHNICAL;       autorizzato = user.isTechnical(); }
                case "COLLABORATOR" -> { tipo = PersonType.COLLABORATOR;    autorizzato = user.isCollaborator(); }
                case "ADMINISTRATIVE"->{ tipo = PersonType.ADMINISTRATIVE;  autorizzato = user.isAdministrative(); }
                default             -> {sendError(response, 400, "Invalid role selected.") ;
                return;}
                }
            
            if (!autorizzato) {
            	sendError(response, 403, "You are not enabled to access as " + tipoStr + ".");
            
                return;
            }

            HttpSession session = request.getSession(true);
            session.setAttribute("user", user);
            session.setAttribute("administrative", user.isAdministrative());
            session.setAttribute("technical", user.isTechnical());
            session.setAttribute("collaborator", user.isCollaborator());
            session.setAttribute("role", tipo);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("role", tipoStr);
            result.put("firstName", user.getFirstName());
            result.put("lastName", user.getLastName());
            result.put("username", user.getUsername());

            sendJsonResponse(response, 200, result);
        } catch (SQLException e) {
            throw new ServletException("Error during login", e);
        }
    }


}