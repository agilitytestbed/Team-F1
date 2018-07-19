package nl.utwente.ing.controller.database;

import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DBUtil {

    /**
     * Executes a delete on the database.
     * Expects the first parameter of the input query to be the ID of the object to delete and the second one
     * to be the session ID of the owner of this object.
     * Example: DELETE FROM transactions WHERE transaction_id = ? AND session_id = ?
     *
     * @param response  the HttpServletResponse which will be updated with the result of the update
     * @param query     the query which is to be executed, which should contain two parameters. The first parameter should
     *                  be the ID of the object to delete, and the second parameter should be the session ID of the owner
     *                  of this object.
     * @param id        the ID of the object to delete
     * @param sessionID the session ID of the owner of the object to delete
     */
    public static void executeDelete(HttpServletResponse response, String query, int id, String sessionID) {
        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)
        ) {
            statement.setInt(1, id);
            statement.setString(2, sessionID);
            if (statement.executeUpdate() == 1) {
                connection.commit();
                response.setStatus(204);
            } else {
                connection.rollback();
                response.setStatus(404);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
        }
    }
}
