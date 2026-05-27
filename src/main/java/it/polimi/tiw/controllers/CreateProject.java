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
import jakarta.servlet.http.HttpSession;

import it.polimi.tiw.beans.User;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.UserDAO;


@WebServlet("/create-project")
public class CreateProject extends AbstractServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		HttpSession session = request.getSession(false);
		User admin = (User) session.getAttribute("user");

		String title = request.getParameter("title");
		String durationMonth = request.getParameter("durationMonth");
		String managerId = request.getParameter("managerId");

		// valori grezzi da ripresentare in caso di errore
		Map<String, String> formData = new HashMap<>();
		formData.put("p_title", title);
		formData.put("p_durationMonth", durationMonth);
		formData.put("p_managerId", managerId);

		try {
			if (title == null || title.isBlank()) {
				fail(request, response, "The title of the project is mandatory", formData);
				return;
			}
			int durMonth;
			try {
				durMonth = Integer.parseInt(durationMonth.trim());
			} catch (NumberFormatException | NullPointerException e) {
				fail(request, response, "The duration must be an integer number", formData);
				return;
			}
			if (durMonth <= 0) {
				fail(request, response, "Duration must be greater than zero", formData);
				return;
			}
			int manId;
			try {
				manId = Integer.parseInt(managerId.trim());
			} catch (NumberFormatException | NullPointerException e) {
				fail(request, response, "You must select a manager", formData);
				return;
			}

			UserDAO userDAO = new UserDAO(connection);
			User manager = userDAO.findById(manId);
			
			if (manager == null || !manager.isTechnical()) {
				fail(request, response, "The selected manager is not valid", formData);
				return;
			}

			ProjectDAO projectDAO = new ProjectDAO(connection);
			projectDAO.createProject(title.trim(), durMonth, admin.getId(), manId);

			String msg = URLEncoder.encode("Project created successfully", StandardCharsets.UTF_8);
			response.sendRedirect(request.getContextPath() + "/home-admin?successMsg=" + msg);

		} catch (SQLException e) {
			throw new ServletException("Error while creating the project", e);
		}
	}

	private void fail(HttpServletRequest request, HttpServletResponse response, String errorMsg, Map<String, String> formData) throws ServletException, IOException {
		request.setAttribute("errorMsg", errorMsg);
		request.setAttribute("formData", formData);
		request.getRequestDispatcher("/home-admin").forward(request, response);
	}
}