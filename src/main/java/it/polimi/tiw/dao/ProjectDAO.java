package it.polimi.tiw.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import it.polimi.tiw.beans.Project;
import it.polimi.tiw.beans.State;
import it.polimi.tiw.beans.WorkPackage;

public class ProjectDAO {
	private final Connection connection;

    public ProjectDAO(Connection connection) {
        this.connection = connection;
    }
    
    public int createProject(String title, int durationMonths, int administratorId, int managerId) throws SQLException {
    	String query = "INSERT INTO projects (title, duration_months, state, administrator_id, manager_id) VALUES (?, ?, 'CREATED', ?, ?)";
    	try (PreparedStatement ps = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setInt(2, durationMonths);
            ps.setInt(3, administratorId);
            ps.setInt(4, managerId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
    	throw new SQLException("Project creation failed: no id was generated");
    }
    
    public Project findById(int id) throws SQLException {
        String query = "SELECT p.*, u.first_name AS f_name, u.last_name AS l_name "
        		+ "FROM projects p JOIN users u ON p.manager_id = u.id WHERE p.id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return buildProject(rs);
                return null;
            }
        }
    }
    
    public List<Project> findByAdmin(int adminId) throws SQLException {
    	String query = "SELECT p.*, u.first_name AS admin_first_name, u.last_name AS admin_last_name "
    			+ "FROM projects p JOIN users u ON p.manager_id = u.id "
    			+ "WHERE p.administrator_id = ? "
    			+ "ORDER BY p.title";
    	return queryProjects(query, adminId);
    }
    
    public List<Project> findCreatedByAdmin(int adminId) throws SQLException {
        String query = "SELECT p.*, u.first_name AS f_name, u.last_name AS l_name "
        		+ "FROM projects p JOIN users u ON p.manager_id = u.id "
        		+ "WHERE p.administrator_id = ? AND p.state = 'CREATED' "
        		+ "ORDER BY p.title";
        return queryProjects(query, adminId);
    }
    
    public List<Project> findByCollaborator(int collabId) throws SQLException {
        String query = "SELECT DISTINCT p.*, u.first_name AS r_first_name, u.last_name AS r_last_name "
                     + "FROM projects p "
                     + "JOIN users u       ON p.manager_id = u.id "
                     + "JOIN work_packages wp ON wp.project_id = p.id "
                     + "JOIN tasks t          ON t.wp_id = wp.id "
                     + "JOIN task_assignments a  ON a.task_id = t.id "
                     + "WHERE a.collaborator_id = ? ORDER BY p.title";
        return queryProjects(query, collabId);
    }
    
    public List<Project> findByManager(int managerId) throws SQLException {
        String query = "SELECT p.*, u.first_name AS r_first_name, u.last_name AS r_last_name "
                     + "FROM projects p JOIN users u ON p.manager_id = u.id "
                     + "WHERE p.manager_id = ? ORDER BY p.title";
        return queryProjects(query, managerId);
    }

    
    public void loadStructure(Project project) throws SQLException {
        WorkPackageDAO wpDAO = new WorkPackageDAO(connection);
        TaskDAO taskDAO = new TaskDAO(connection);

        List<WorkPackage> wps = wpDAO.findByProject(project.getId());
        for (WorkPackage wp : wps) {
            wp.setTasks(taskDAO.findByWorkPackage(wp.getId()));
        }
        project.setWorkPackages(wps);
    }
    
    public void updateState(int projectId, String newState) throws SQLException {
        String sql = "UPDATE projects SET state = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newState);
            ps.setInt(2, projectId);
            ps.executeUpdate();
        }
    }
    
    private List<Project> queryProjects(String query, int param) throws SQLException {
        List<Project> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(buildProject(rs));
            }
        }
        return result;
    }
    
    private Project buildProject(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.setId(rs.getInt("id"));
        p.setTitle(rs.getString("title"));
        p.setDurationMonths(rs.getInt("duration_months"));
        p.setState(State.fromDB(rs.getString("state")));
        p.setAdministratorId(rs.getInt("administrator_id"));
        p.setManagerId(rs.getInt("manager_id"));
        return p;
    }
}
