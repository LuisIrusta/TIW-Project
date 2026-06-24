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


import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.Task;
import it.polimi.tiw.beans.User;
import it.polimi.tiw.beans.WorkPackage;
import it.polimi.tiw.dao.HoursDAO;
import it.polimi.tiw.dao.ProjectDAO;
import it.polimi.tiw.dao.TaskDAO;
import it.polimi.tiw.dao.WorkPackageDAO;


@WebServlet("/api/home-collaborator")
public class GoToHomeCollaborator extends AbstractServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            sendJsonResponse(response, 401, Map.of("error", "Sessione scaduta o non valida. Effettua il login."));
            return;
        }
        User collaborator = (User) session.getAttribute("user");
        
        if (!collaborator.isCollaborator()) { 
            sendJsonResponse(response, 403, Map.of("error", "Accesso negato. Non hai i permessi necessari."));
            return;
        }

        try {
            ProjectDAO projectDAO = new ProjectDAO(connection);
            WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
            TaskDAO taskDAO = new TaskDAO(connection);
            HoursDAO hoursDAO = new HoursDAO(connection);

            // 2. RECUPERA TUTTI I PROGETTI DEL COLLABORATORE
            List<Project> projects = projectDAO.findByCollaborator(collaborator.getId());

            for (Project p : projects) {
                // Recupera tutti i WP per questo specifico progetto e collaboratore
                List<WorkPackage> wps = wpDAO.findByProjectAndCollaborator(p.getId(), collaborator.getId());
                
                for (WorkPackage wp : wps) {
                    // Recupera tutti i task di questo WP assegnati al collaboratore
                    List<Task> tasks = taskDAO.findByWorkPackageAndCollaborator(wp.getId(), collaborator.getId());
                    
                    for (Task t : tasks) {
                        int totPlanned = hoursDAO.getTotalPlannedForTask(t.getId());
                        int totWorked = hoursDAO.getTotalWorkedForCollaboratorTask(t.getId(), collaborator.getId());
                        t.setTotalPlannedHours(totPlanned);
                        t.setTotalWorkedHours(totWorked);
                    }
                    wp.setTasks(tasks);
               }
                p.setWorkPackages(wps);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("collaboratore", collaborator);
            result.put("progetti", projects); 

            sendJsonResponse(response, 200, result);

        } catch (SQLException e) {
            throw new ServletException("Errore nel caricamento completo dell'albero per HOME COLLABORATORE", e);
        }
    }
}