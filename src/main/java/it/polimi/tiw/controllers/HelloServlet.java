package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.thymeleaf.context.WebContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//TODO Delete this class after log-in is done. This is just a testing class to see if the web services are functioning

@WebServlet("/hello")
public class HelloServlet extends AbstractServlet {

    private static final long serialVersionUID = 1L;

	@Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        // 1) probar que la conexión funciona dentro de Tomcat: contar usuarios
        int total = 0;
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) total = rs.getInt(1);
        } catch (SQLException e) {
            throw new ServletException(e);
        }

        // 2) probar que Thymeleaf renderiza
        WebContext ctx = getWebContext(req, res);
        ctx.setVariable("total", total);
        templateEngine.process("hello", ctx, res.getWriter());
    }
}