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
        String query = "SELECT id, username, first_name, last_name, photo, role FROM users WHERE username = ? AND password_hash = ?";
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
        String query = "SELECT id, username, first_name, last_name, photo, role FROM users WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return buildUser(rs);
                return null;
            }
        }
    }
    
    public List<User> findAllTechnicals() throws SQLException{
    	String query = "SELECT id, username, first_name, last_name, photo, role FROM users WHERE role = 'technical' ORDER BY last_name, first_name";
    	List<User> result = new ArrayList<>();
    	try (PreparedStatement ps = connection.prepareStatement(query);
                ResultSet rs = ps.executeQuery()) {
               while (rs.next()) result.add(buildUser(rs));
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
        u.setPersonType(PersonType.fromDB(rs.getString("role")));
        return u;
    }
}
