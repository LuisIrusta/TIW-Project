package it.polimi.tiw.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.UnavailableException;

public class ConnectionHandler {

    public static Connection getConnection(ServletContext context) throws UnavailableException {
        Connection connection = null;
        try {
            String driver = context.getInitParameter("dbDriver");
            String url    = context.getInitParameter("dbUrl");
            String user   = context.getInitParameter("dbUser");
            String pass   = context.getInitParameter("dbPassword");
            Class.forName(driver);
            connection = DriverManager.getConnection(url, user, pass);
        } catch (ClassNotFoundException e) {
            throw new UnavailableException("Driver JDBC not found");
        } catch (SQLException e) {
            throw new UnavailableException("Impossible to connect to the DB");
        }
        return connection;
    }

    public static void closeConnection(Connection connection) throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}