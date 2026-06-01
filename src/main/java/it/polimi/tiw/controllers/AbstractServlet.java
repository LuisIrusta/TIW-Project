package it.polimi.tiw.controllers;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import it.polimi.tiw.utils.ConnectionHandler;
import it.polimi.tiw.utils.TemplateEngineUtil;

public abstract class AbstractServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    protected Connection connection;
    protected TemplateEngine templateEngine;

    @Override
    public void init() throws ServletException {
        this.connection = ConnectionHandler.getConnection(getServletContext());
        this.templateEngine = TemplateEngineUtil.getTemplateEngine(getServletContext());
    }

    @Override
    public void destroy() {
        try {
            ConnectionHandler.closeConnection(connection);
        } catch (SQLException e) {

        }
    }

    protected WebContext getWebContext(HttpServletRequest request, HttpServletResponse response) {
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(getServletContext());
        IWebExchange webExchange = application.buildExchange(request, response);
        return new WebContext(webExchange, request.getLocale());
    }

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
    protected String extractTriggerMessage(SQLException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("45000")) {
            int idx = msg.lastIndexOf("45000");
            return msg.substring(idx + 6).trim();
        }
        return "Database error.";
    }
}