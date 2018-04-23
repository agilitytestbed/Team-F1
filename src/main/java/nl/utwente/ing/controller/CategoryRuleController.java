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

import com.google.gson.*;
import nl.utwente.ing.controller.database.DBConnection;
import nl.utwente.ing.controller.database.DBUtil;
import nl.utwente.ing.model.CategoryRule;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categoryRules")
public class CategoryRuleController {

    /**
     * Returns a list of all the category rules that are available to the session ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @param response the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of all category rules
     * @see CategoryRule
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getCategoryRules(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                   @RequestParam(value = "session_id", required = false) String querySessionID,
                                   HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        String query = "SELECT c.categoryrule_id, c.description, c.iban, c.type, c.category_id, c.apply_on_history " +
                "FROM categoryrules c " +
                "WHERE c.session_id = ?;";

        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)
        ){
            preparedStatement.setString(1, sessionID);
            ResultSet resultSet = preparedStatement.executeQuery();

            List<CategoryRule> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(new CategoryRule(
                        resultSet.getInt("categoryrule_id"),
                        resultSet.getString("description"),
                        resultSet.getString("iban"),
                        resultSet.getString("type"),
                        resultSet.getInt("category_id"),
                        resultSet.getBoolean("apply_on_history"))
                );
            }

            response.setStatus(200);
            return new GsonBuilder().create().toJson(results);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }
    }

    /**
     * Creates a new CategoryRule that is linked to the current session ID. Expects the body to be formatted according
     * to the <a href="https://app.swaggerhub.com/apis/djhuistra/INGHonours-CategoryRules/">API specification</a>.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @param body the request body containing the JSON representation of the CategoryRule to add
     * @param response the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the newly added CategoryRule
     * @see CategoryRule
     */
    @RequestMapping(value = "", method = RequestMethod.POST, produces = "application/json")
    public String addCategoryRule(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @RequestBody String body,
                                  HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        try {
            Gson gson = new Gson();
            CategoryRule categoryRule = gson.fromJson(body, CategoryRule.class);

            if (categoryRule.getDescription() == null || categoryRule.getIban() == null
                    || categoryRule.getType() == null || categoryRule.getCategoryId() == null) {
                throw new JsonSyntaxException("CategoryRule is missing one or more elements");
            }

            if (categoryRule.shouldApplyOnHistory()) {
                // This query matches either the empty string (wildcard value) or the exact value.
                String updateQuery = "UPDATE transactions SET category_id = ? " +
                        "WHERE (? = \"\" OR description = ?) " +
                        "AND (? = \"\" OR external_iban = ?) " +
                        "AND (? = \"\" OR type = ?);";

                try (Connection connection = DBConnection.instance.getConnection();
                     PreparedStatement updateStatement = connection.prepareStatement(updateQuery)
                ) {
                    updateStatement.setInt(1, categoryRule.getCategoryId());
                    updateStatement.setString(2, categoryRule.getDescription());
                    updateStatement.setString(3, categoryRule.getDescription());
                    updateStatement.setString(4, categoryRule.getIban());
                    updateStatement.setString(5, categoryRule.getIban());
                    updateStatement.setString(6, categoryRule.getType().toLowerCase());
                    updateStatement.setString(7, categoryRule.getType().toLowerCase());
                    updateStatement.executeUpdate();
                } catch (SQLException | IllegalArgumentException e) {
                    e.printStackTrace();
                    response.setStatus(500);
                    return null;
                }
            }

            String insertQuery = "INSERT INTO categoryrules (description, iban, type, category_id, apply_on_history, session_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?);";
            String resultQuery = "SELECT last_insert_rowid() FROM categories LIMIT 1;";

            try (Connection connection = DBConnection.instance.getConnection();
                 PreparedStatement insertStatement = connection.prepareStatement(insertQuery);
                 PreparedStatement resultStatement = connection.prepareStatement(resultQuery)
            ) {
                insertStatement.setString(1, categoryRule.getDescription());
                insertStatement.setString(2, categoryRule.getIban());
                insertStatement.setString(3, categoryRule.getType().toLowerCase());
                insertStatement.setInt(4, categoryRule.getCategoryId());
                insertStatement.setBoolean(5, categoryRule.shouldApplyOnHistory());
                insertStatement.setString(6, sessionID);
                if (insertStatement.executeUpdate() != 1) {
                    response.setStatus(405);
                    return null;
                }

                ResultSet resultSet = resultStatement.executeQuery();
                if (resultSet.next()) {
                    categoryRule.setId(resultSet.getInt(1));
                    response.setStatus(201);
                    return new GsonBuilder().create().toJson(categoryRule);
                } else {
                    response.setStatus(405);
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
     * Returns a specific CategoryRule corresponding to the category rule ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @param id the category rule ID corresponding to the category rule to return
     * @param response the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the specified CategoryRule
     * @see CategoryRule
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = "application/json")
    public String getCategoryRule(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @PathVariable("id") int id,
                                  HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        String query = "SELECT c.categoryrule_id, c.description, c.iban, c.type, c.category_id, c.apply_on_history " +
                "FROM categoryrules c " +
                "WHERE c.categoryrule_id = ? " +
                "AND c.session_id = ?;";

        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)
        ) {
            statement.setInt(1, id);
            statement.setString(2, sessionID);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                response.setStatus(200);
                CategoryRule result = new CategoryRule(
                        resultSet.getInt("categoryrule_id"),
                        resultSet.getString("description"),
                        resultSet.getString("iban"),
                        resultSet.getString("type"),
                        resultSet.getInt("category_id"),
                        resultSet.getBoolean("apply_on_history"));
                return new GsonBuilder().create().toJson(result);
            } else {
                response.setStatus(404);
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }
    }

    /**
     * Updates the given category rule corresponding to the category rule ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @param id the category rule ID corresponding to the category rule to update
     * @param body the request body containing the JSON representation of the CategoryRule to update
     * @param response the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the updated CategoryRule
     * @see CategoryRule
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, produces = "application/json")
    public String putCategoryRule(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @PathVariable("id") int id,
                                  @RequestBody String body,
                                  HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        try {
            Gson gson = new Gson();
            CategoryRule categoryRule = gson.fromJson(body, CategoryRule.class);
            categoryRule.setId(id);

            if (categoryRule.getDescription() == null || categoryRule.getIban() == null
                    || categoryRule.getType() == null || categoryRule.getId() == null) {
                throw new JsonSyntaxException("CategoryRule is missing one or more elements");
            }


            //String insertQuery = "INSERT INTO categoryrules (description, iban, type, category_id, apply_on_history, session_id) " +
            String query = "UPDATE categoryrules SET description = ? , iban = ?, type = ?, category_id = ? " +
                    "WHERE categoryrule_id = ? AND session_id = ?";

            try (Connection connection = DBConnection.instance.getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)
            ) {
                statement.setString(1, categoryRule.getDescription());
                statement.setString(2, categoryRule.getIban());
                statement.setString(3, categoryRule.getType().toLowerCase());
                statement.setInt(4, categoryRule.getCategoryId());
                statement.setInt(5, id);
                statement.setString(6, sessionID);
                if (statement.executeUpdate() == 1) {
                    response.setStatus(200);
                    return new GsonBuilder().create().toJson(categoryRule);
                } else {
                    response.setStatus(404);
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
     * Deletes the category rule corresponding to the given category rule ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @param id the category rule ID corresponding to the category to delete
     * @param response the response shown to the user, necessary to edit the status code of the response
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void deleteCategoryRule(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                   @RequestParam(value = "session_id", required = false) String querySessionID,
                                   @PathVariable("id") int id,
                                   HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;
        String query = "DELETE FROM categoryrules WHERE categoryrule_id = ? AND session_id = ?";
        DBUtil.executeDelete(response, query, id, sessionID);
    }
}
