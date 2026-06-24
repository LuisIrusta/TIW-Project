package it.polimi.tiw.controllers;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/logout")
public class Logout extends AbstractServlet {
    private static final long serialVersionUID = 1L;

    public Logout() {
        super();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Recupera la sessione corrente, ma non crearne una nuova se non esiste (false)
        HttpSession session = request.getSession(false);
        
        if (session != null) {
            // Invalida la sessione (cancella tutti i dati dell'utente associati)
            session.invalidate();
        }

        // Prepara una risposta JSON strutturata usando il metodo della classe astratta
        Map<String, Object> successResponse = Map.of(
            "success", true,
            "message", "Logout effettuato con successo."
        );
        
        // Invia la risposta con codice 200 OK
        sendJsonResponse(response, HttpServletResponse.SC_OK, successResponse);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // Se qualcuno prova ad accedere in GET, reindirizzalo al POST o restituisci un errore
        sendError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Metodo non consentito. Usa POST per il logout.");
    }
}