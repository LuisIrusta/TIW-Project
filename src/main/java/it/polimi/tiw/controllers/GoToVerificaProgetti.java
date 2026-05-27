package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.thymeleaf.context.WebContext;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.ProjectDAO;


@WebServlet("/verifica-progetti")
public class GoToVerificaProgetti extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User admin = (User) session.getAttribute("user");

        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            List<Project> projects = projectDAO.findByAdmin(admin.getId());
            int selectedId = parseIntOrDefault(request.getParameter("projectId"), -1);
            Project selected = null;
            for (Project p : projects) {
                if (p.getId() == selectedId) {
                    selected = p;
                    break;
                }
            }
            if (selected == null && !projects.isEmpty()) {
                selected = projects.get(0);
            }
            if (selected != null) {
                projectDAO.loadStructure(selected);
            }
            WebContext ctx = getWebContext(request, response);
            ctx.setVariable("admin", admin);
            ctx.setVariable("projects", projects);
            ctx.setVariable("selected", selected);
            templateEngine.process("verificaProgetti", ctx, response.getWriter());

        } catch (SQLException e) {
            throw new ServletException("Error while loading the page VERIFICA PROGETTI", e);
        }
    }
}