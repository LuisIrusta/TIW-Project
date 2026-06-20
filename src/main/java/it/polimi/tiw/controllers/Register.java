package it.polimi.tiw.controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import it.polimi.tiw.beans.*;
import it.polimi.tiw.dao.UserDAO;

@WebServlet("/api/register")
public class Register extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        JsonObject jsonRequest = readJsonRequest(request);
        if (jsonRequest == null) {
            sendJsonResponse(response, 400, Map.of("success", false, "error", "Payload JSON mancante o malformato."));
            return;
        }

        // 3. Estraiamo i parametri dal JSON (sostituisce request.getParameter)
        // Usiamo un controllo per evitare NullPointerException se una chiave manca nel JSON
        String firstName = jsonRequest.has("first_name") ? jsonRequest.get("first_name").getAsString() : null;
        String lastName  = jsonRequest.has("last_name")  ? jsonRequest.get("last_name").getAsString() : null;
        String username  = jsonRequest.has("username")   ? jsonRequest.get("username").getAsString() : null;
        String password  = jsonRequest.has("password")   ? jsonRequest.get("password").getAsString() : null;
        String conferma  = jsonRequest.has("conferma")   ? jsonRequest.get("conferma").getAsString() : null;
        String roleParam = jsonRequest.has("role")       ? jsonRequest.get("role").getAsString() : null;

        // --- DA QUI IN POI LA LOGICA È IDENTICA ALLA TUA ---

        PersonType userRole = null;
        if (roleParam != null) {
            try {
                userRole = PersonType.valueOf(roleParam.toUpperCase());
            } catch (IllegalArgumentException e) {
                userRole = null;
            }
        }

        if (firstName == null || firstName.isBlank() ||
            lastName  == null || lastName.isBlank()  ||
            username  == null || username.isBlank()  ||
            password  == null || password.isBlank()  ) {
            sendError(response, 400, "Please fill in all mandatory fields.");
            return;
        }
        if (!password.equals(conferma)) {
            sendError(response, 400, "Passwords do not match.");
            return;
        }
        if (userRole == null) {
            sendError(response, 400, "Seleziona almeno un ruolo.");
            return;
        }

        try {
            UserDAO userDAO = new UserDAO(connection);

            if (userDAO.findByUsername(username.trim()) != null) {
                sendError(response, 409, "Username already in use.");
                return;
            }

            String hash = password;
            userDAO.createUser(username, hash, firstName, lastName, userRole);
            sendJsonResponse(response, HttpServletResponse.SC_OK, Map.of("success", true));
        } catch (SQLException e) {
            throw new ServletException("Error during registration", e);
        }
    }
}