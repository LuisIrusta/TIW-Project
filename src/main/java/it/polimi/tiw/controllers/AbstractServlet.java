package it.polimi.tiw.controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import it.polimi.tiw.utils.ConnectionHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public abstract class AbstractServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected Connection connection;

    @Override
    public void init() throws ServletException {
        this.connection = ConnectionHandler.getConnection(getServletContext());
    }

    @Override
    public void destroy() {
        try {
            ConnectionHandler.closeConnection(connection);
        } catch (SQLException e) {
            // Loggare l'errore è una buona pratica
        }
    }
    protected JsonObject readJsonRequest(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        try {
            return new Gson().fromJson(sb.toString(), JsonObject.class);
        } catch (Exception e) {
            return null; // Ritorna null se il JSON è malformato
        }
    }



    /**
     * Invia dati (liste, oggetti, bean) serializzati in JSON con uno status specifico.
     */
    protected void sendJsonResponse(HttpServletResponse response, int statusCode, Object data) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);
        
        String json = new Gson().toJson(data);
        response.getWriter().write(json);
    }

    /**
     * Scorciatoia per inviare messaggi d'errore (stringhe) formattati in JSON.
     * Risolve l'errore del "method sendError undefined".
     */
    protected void sendError(HttpServletResponse response, int statusCode, String errorMessage) throws IOException {
        // Creiamo una mappa al volo così il JSON in uscita sarà un oggetto strutturato
        Map<String, Object> errorStructure = Map.of(
            "success", false,
            "error", errorMessage
        );
        sendJsonResponse(response, statusCode, errorStructure);
    }

    /**
     * Mantiene la stessa logica per il parsing dei parametri numerici di input.
     */
    protected int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}