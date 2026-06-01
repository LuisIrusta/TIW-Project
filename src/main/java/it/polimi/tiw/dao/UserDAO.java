package it.polimi.tiw.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.User;

public class UserDAO {
	private final Connection connection;

    public UserDAO(Connection connection) {
        this.connection = connection;
    }
    
    public User checkCredentials(String username, String password) throws SQLException {
        String query = "SELECT id, username, first_name, last_name, photo, administrative  FROM users WHERE username = ? AND password_hash = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return buildUser(rs);
                }
                return null;
            }
        }
    }
    
    public User findById(int id) throws SQLException {
    	String query = "SELECT id, username, password_hash, first_name, last_name, photo, " +
                "administrative, technical, collaborator FROM users WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return buildUser(rs);
                return null;
            }
        }
    }
    public User findByUsername(String userN) throws SQLException {
    	String query = "SELECT id, username, password_hash, first_name, last_name, photo, " +
                "administrative, technical, collaborator FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, userN);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return buildUser(rs);
                return null;
            }
        }
    }
    
    public List<User> findAllTechnicals() throws SQLException{
    	String query = "SELECT id, username, first_name, last_name, photo, administrative, technical, collaborator  FROM users WHERE technical = 1";
    	List<User> result = new ArrayList<>();
    	try (PreparedStatement ps = connection.prepareStatement(query);
                ResultSet rs = ps.executeQuery()) {
               while (rs.next()) result.add(buildUser(rs));
           }
           return result;
    }
    public List<User> findAssignedToTask(int taskId) throws SQLException {
        String query = "SELECT u.id, u.username, u.password_hash, u.first_name, u.last_name, u.photo, " +
                       "u.administrative, u.technical, u.collaborator " +
                       "FROM users u JOIN task_assignments ta ON ta.collaborator_id = u.id " +
                       "WHERE ta.task_id = ? ORDER BY u.last_name, u.first_name";
        List<User> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(buildUser(rs));
            }
        }
        return result;
    }

    public List<User> findAvailableCollaborators(int taskId, int managerId) throws SQLException {
        String query = "SELECT id, username, password_hash, first_name, last_name, photo, " +
                       "administrative, technical, collaborator FROM users " +
                       "WHERE collaborator = 1 AND id != ? " +
                       "AND id NOT IN (SELECT collaborator_id FROM task_assignments WHERE task_id = ?) " +
                       "ORDER BY last_name, first_name";
        List<User> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, managerId);
            ps.setInt(2, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(buildUser(rs));
            }
        }
        return result;
    }

    public List<User> findCollaboratorsByManager(int managerId) throws SQLException {
        String query = "SELECT DISTINCT u.id, u.username, u.password_hash, u.first_name, u.last_name, u.photo, " +
                       "u.administrative, u.technical, u.collaborator " +
                       "FROM users u " +
                       "JOIN task_assignments ta ON ta.collaborator_id = u.id " +
                       "JOIN tasks t ON t.id = ta.task_id " +
                       "JOIN work_packages w ON w.id = t.wp_id " +
                       "JOIN projects p ON p.id = w.project_id " +
                       "WHERE p.manager_id = ? ORDER BY u.last_name, u.first_name";
        List<User> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, managerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(buildUser(rs));
            }
        }
        return result;
    }
    
    private User buildUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setFirstName(rs.getString("first_name"));
        u.setLastName(rs.getString("last_name"));
        u.setPhoto(rs.getString("photo"));
        u.setAdministrative(rs.getInt("administrative") == 1);
        u.setTechnical(rs.getInt("technical") == 1);
        u.setCollaborator(rs.getInt("collaborator") == 1);
        return u;
    }
    public void createUser(String username, String hash, String firstName, String lastName, 
            boolean administrative, boolean technical, boolean collaborator) throws SQLException {
    			String query = "INSERT INTO users (username, password_hash, first_name, last_name, administrative, technical, collaborator) VALUES (?, ?, ?, ?, ?, ?, ?)";

    						try (PreparedStatement ps = connection.prepareStatement(query)) {
    								ps.setString(1, username.trim());
    								ps.setString(2, hash);
    								ps.setString(3, firstName.trim());
    								ps.setString(4, lastName.trim());
    								ps.setBoolean(5, administrative);
    								ps.setBoolean(6, technical);
    								ps.setBoolean(7, collaborator);
    								ps.executeUpdate(); 
    						}
    				}	

	
}
