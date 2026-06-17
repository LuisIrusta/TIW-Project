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


@WebServlet("/monitor-projects")
public class GoToMonitorProjects extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        process(request, response);
    }
    
    private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        User manager = (User) session.getAttribute("user");

        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);
            
            List<Project> projects = new ArrayList<>();
            for (Project p : projectDAO.findByManager(manager.getId())) {
                if (p.isAssigned() || p.isConcluded()) projects.add(p);
            }
            int selectedId = parseIntOrDefault(request.getParameter("projectId"), -1);
            Project selected = null;
            for (Project p : projects) {
                if (p.getId() == selectedId) { selected = p; break; }
            }
            if (selected == null && !projects.isEmpty()) {
                selected = projects.get(0);
            }
            List<Integer> months = null;
            boolean canConclude = false;
            if (selected != null) {
                projectDAO.loadStructure(selected);
                int duration = selected.getDurationMonths();
                months = new ArrayList<>();
                for (int m = 1; m <= duration; m++) months.add(m);
                for (WorkPackage wp : selected.getWorkPackages()) {
                    for (Task t : wp.getTasks()) {
                        List<MonthHours> active = hoursDAO.getMonthHoursForTask(t.getId(), t.getStartMonth(), t.getEndMonth());
                        Map<Integer, MonthHours> byMonth = new HashMap<>();
                        for (MonthHours mh : active) byMonth.put(mh.getMonth(), mh);
                        t.setMonthHours(buildFullRow(byMonth, duration));
                    }
                    wp.setMonthHours(buildWpRow(wp, duration));
                }
                canConclude = selected.isAssigned() && hoursDAO.canConcludeProject(selected.getId());
            }

            WebContext ctx = getWebContext(request, response);
            ctx.setVariable("responsabile", manager);
            ctx.setVariable("progetti", projects);
            ctx.setVariable("selected", selected);
            ctx.setVariable("mesi", months);
            ctx.setVariable("canConclude", canConclude);
            ctx.setVariable("successMsg", request.getParameter("successMsg"));
            ctx.setVariable("errorMsg", request.getAttribute("errorMsg"));
            templateEngine.process("MonitorProjects", ctx, response.getWriter());

        } catch (SQLException e) {
            throw new ServletException("Error while loading the page MONITOR PROJECTS", e);
        }
    }


    
    private List<MonthHours> buildFullRow(Map<Integer, MonthHours> byMonth, int duration) {
        List<MonthHours> full = new ArrayList<>();
        for (int m = 1; m <= duration; m++) {
            if (byMonth.containsKey(m)) {
                full.add(byMonth.get(m));
            } else {
                full.add(new MonthHours(m, false));   // cella vuota
            }
        }
        return full;
    }

    private List<MonthHours> buildWpRow(WorkPackage wp, int duration) {
        List<MonthHours> wpRow = new ArrayList<>();
        for (int m = 1; m <= duration; m++) {
            boolean wpActive = (m >= wp.getStartMonth() && m <= wp.getEndMonth());
            MonthHours cell = new MonthHours(m, wpActive);
            if (wpActive) {
                int prev = 0, lav = 0;
                for (Task t : wp.getTasks()) {
                    MonthHours tmh = t.getMonthHours().get(m - 1);
                    if (tmh.isActive()) {
                        prev += tmh.getPlannedHours();
                        lav += tmh.getWorkedHours();
                    }
                }
                cell.setPlannedHours(prev);
                cell.setWorkedHours(lav);
            }
            wpRow.add(cell);
        }
        return wpRow;
    }
}