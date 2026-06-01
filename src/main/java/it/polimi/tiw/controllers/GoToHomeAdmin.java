package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
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
import it.polimi.tiw.dao.UserDAO;
import it.polimi.tiw.dao.WorkPackageDAO;


@WebServlet("/home-admin")
public class GoToHomeAdmin extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        showHome(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        showHome(request, response);
    }

    private void showHome(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User admin = (User) session.getAttribute("user");

        try {
            UserDAO userDAO = new UserDAO(connection);
            ProjectDAO projectDAO = new ProjectDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);

            List<User> technicals = userDAO.findAllTechnicals();

            List<Project> createdProjects = projectDAO.findCreatedByAdmin(admin.getId());

            for (Project p : createdProjects) {
                p.setWorkPackages(wpDAO.findByProject(p.getId()));
            }

            WebContext ctx = getWebContext(request, response);
            ctx.setVariable("admin", admin);
            ctx.setVariable("technicals", technicals);
            ctx.setVariable("createdProjects", createdProjects);

            String successMsg = request.getParameter("successMsg");
            ctx.setVariable("errorMsg", request.getAttribute("errorMsg"));
            ctx.setVariable("successMsg", successMsg);
            Object fd = request.getAttribute("formData");
            ctx.setVariable("formData", fd != null ? fd : Collections.emptyMap());
            
            templateEngine.process("home-admin", ctx, response.getWriter());

        } catch (SQLException e) {
            throw new ServletException("Error while loading the home administrator page", e);
        }
    }
}