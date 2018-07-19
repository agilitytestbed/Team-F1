/*
 * Copyright (c) 2018, Tom Leemreize <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nl.utwente.ing.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import nl.utwente.ing.controller.database.DBConnection;
import nl.utwente.ing.controller.database.DBUtil;
import nl.utwente.ing.model.SavingsGoal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/savingGoals")
public class SavingsGoalController {

    /**
     * Returns a list of all the savings goals defined by the current session ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @param response the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of all savings goals
     * @see SavingsGoal
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getSavingsGoals(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  HttpServletResponse response) {
        String sessionID = headerSessionID != null ? headerSessionID : querySessionID;

        String query = "SELECT * FROM savingsgoals WHERE session_id = ?;";

        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)
        ) {
            preparedStatement.setString(1, sessionID);
            ResultSet resultSet = preparedStatement.executeQuery();

            List<SavingsGoal> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(new SavingsGoal(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getInt("goal"),
                        resultSet.getInt("monthly"),
                        resultSet.getInt("minbalance"),
                        resultSet.getInt("balance"))
                );
            }

            response.setStatus(200);
            connection.commit();
            return new GsonBuilder().create().toJson(results);
        } catch(SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }
    }

    /**
     * Creates a new SavingsGoal that is linked to the current session ID. Expects the body to be formatted according
     * to the <a href="https://app.swaggerhub.com/apis/djhuistra/INGHonours-SavingsGoals/">API specification</a>.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @param body the request body containing the JSON representation of the SavingsGoal to add
     * @param response the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the newly added SavingsGoal
     * @see SavingsGoal
     */
    @RequestMapping(value = "", method = RequestMethod.POST, produces = "application/json")
    public String addSavingsGoal(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                 @RequestParam(value = "session_id", required = false) String querySessionID,
                                 @RequestBody String body,
                                 HttpServletResponse response) {
        String sessionID = headerSessionID != null ? headerSessionID : querySessionID;

        try {
            Gson gson = new Gson();
            SavingsGoal savingsGoal = gson.fromJson(body, SavingsGoal.class);

            if (savingsGoal.getName() == null || savingsGoal.getGoal() == null
                    || savingsGoal.getSavePerMonth() == null) {
                throw new JsonSyntaxException("SavingsGoal is missing one or more elements");
            }

            String insertQuery = "INSERT INTO savingsgoals (name, goal, monthly, minbalance, session_id) " +
                    "VALUES (?, ?, ?, ?, ?);";
            String resultQuery = "SELECT last_insert_rowid() LIMIT 1;";

            try (Connection connection = DBConnection.instance.getConnection();
                 PreparedStatement insertStatement = connection.prepareStatement(insertQuery);
                 PreparedStatement resultStatement = connection.prepareStatement(resultQuery)
            ) {
                insertStatement.setString(1, savingsGoal.getName());
                insertStatement.setInt(2, savingsGoal.getGoal());
                insertStatement.setInt(3, savingsGoal.getSavePerMonth());
                insertStatement.setInt(4, savingsGoal.getMinBalanceRequired());
                insertStatement.setString(5, sessionID);

                if (insertStatement.executeUpdate() != 1) {
                    response.setStatus(405);
                    connection.rollback();
                    return null;
                }

                ResultSet resultSet = resultStatement.executeQuery();
                if (resultSet.next()) {
                    savingsGoal.setId(resultSet.getInt(1));
                    savingsGoal.setBalance(0); // A new savings goal always has a balance of 0.
                    response.setStatus(201);
                    connection.commit();
                    return new GsonBuilder().create().toJson(savingsGoal);
                } else {
                    response.setStatus(405);
                    connection.rollback();
                    return null;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.setStatus(500);
                return null;
            }
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            response.setStatus(405);
            return null;
        }
    }

    /**
     * Deletes the savings goal corresponding to the given category rule ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @param id the savings goal ID corresponding to the savings goal to delete
     * @param response the response shown to the user, necessary to edit the status code of the response
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void deleteSavingsGoal(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @PathVariable("id") int id,
                                  HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;
        String query = "DELETE FROM savingsgoals WHERE id = ? AND session_id = ?";
        DBUtil.executeDelete(response, query, id, sessionID);
    }
}
