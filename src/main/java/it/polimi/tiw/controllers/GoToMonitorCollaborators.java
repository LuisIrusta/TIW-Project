package it.polimi.tiw.controllers;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.thymeleaf.context.WebContext;

import it.polimi.tiw.beans.MonthHours;
import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.Task;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.HoursDAO;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.UserDAO;

@WebServlet("/api/monitor-collaborators")
public class GoToMonitorCollaborators extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
        	sendError(response, 401,  "Sessione scaduta. Effettua il login.");
            return;
        }
        User manager = (User) session.getAttribute("user");
        if (!manager.isTechnical()) { 
            sendJsonResponse(response, 403, Map.of("error", "Accesso negato. Non hai i permessi necessari."));
            return;
        }
        try {
            UserDAO userDAO = new UserDAO(connection);
            ProjectDAO projectDAO = new ProjectDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            List<User> collaborators = userDAO.findCollaboratorsOfManager(manager.getId());

            int selectedId = parseIntOrDefault(request.getParameter("collaborator_id"), -1);
            User selected = null;
            for (User c : collaborators) {
                if (c.getId() == selectedId) { selected = c; break; }
            }
            if (selected == null && !collaborators.isEmpty()) {
                selected = collaborators.get(0);
            }
            List<Project> projects = new ArrayList<>();
            if (selected != null) {
                for (Project p : projectDAO.findByCollaborator(selected.getId())) {
                    if (p.getManagerId() == manager.getId()) {
                        projects.add(p);
                    }
                }
                for (Project p : projects) {
                    projectDAO.loadStructure(p);
                    int duration = p.getDurationMonths();
                    for (WorkPackage wp : p.getWorkPackages()) {
                        for (Task t : wp.getTasks()) {
                            List<MonthHours> active = hoursDAO.getMonthWorkedForCollaboratorTask(t.getId(), selected.getId(),t.getStartMonth(), t.getEndMonth());
                            Map<Integer, MonthHours> byMonth = new HashMap<>();
                            for (MonthHours mh : active) byMonth.put(mh.getMonth(), mh);
                            t.setMonthHours(buildFullRow(byMonth, duration));
                        }
                        wp.setMonthHours(buildWpRow(wp, duration));
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("responsabile",      manager);
            result.put("progetti",         projects);
            result.put("selected", selected);
            result.put("collaboratori", collaborators);
            sendJsonResponse(response, 200, result);

        } catch (SQLException e) {
            throw new ServletException("Error while loading MONITOR COLLABORATORS", e);
        }
    }

    private List<MonthHours> buildFullRow(Map<Integer, MonthHours> byMonth, int duration) {
        List<MonthHours> full = new ArrayList<>();
        for (int m = 1; m <= duration; m++) {
            full.add(byMonth.containsKey(m) ? byMonth.get(m) : new MonthHours(m, false));
        }
        return full;
    }

    private List<MonthHours> buildWpRow(WorkPackage wp, int duration) {
        List<MonthHours> wpRow = new ArrayList<>();
        for (int m = 1; m <= duration; m++) {
            boolean wpActive = (m >= wp.getStartMonth() && m <= wp.getEndMonth());
            MonthHours cell = new MonthHours(m, wpActive);
            if (wpActive) {
                int lav = 0;
                for (Task t : wp.getTasks()) {
                    MonthHours tmh = t.getMonthHours().get(m - 1);
                    if (tmh.isActive()) lav += tmh.getWorkedHours();
                }
                cell.setWorkedHours(lav);
            }
            wpRow.add(cell);
        }
        return wpRow;
    }
}