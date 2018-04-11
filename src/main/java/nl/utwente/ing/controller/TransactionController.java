/*
 * Copyright (c) 2018, Joost Prins <github.com/joostprins>, Tom Leemreize <https://github.com/oplosthee>
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
import nl.utwente.ing.model.Category;
import nl.utwente.ing.model.Transaction;
import nl.utwente.ing.model.Type;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("api/v1/transactions")
public class TransactionController {

    /**
     * Returns a list of all the transactions that are available to the session id.
     * @param response to edit the status code of the response
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getAllTransactions(@RequestHeader(value = "X-session-id", required = false) String headerSessionID,
                                     @RequestParam(value = "session_id", required = false) String querySessionID,
                                     @RequestParam(value = "offset", defaultValue = "0") int offset,
                                     @RequestParam(value = "limit", defaultValue = "20") int limit,
                                     @RequestParam(value = "category", required = false) String category,
                                     HttpServletResponse response) {

        String transactionsQuery = "SELECT DISTINCT t.transaction_id, t.date, t.amount, t.description, t.external_iban, t.type, " +
                "CASE WHEN t.category_id IS NULL THEN NULL ELSE c.category_id END AS category_id, " +
                "CASE WHEN t.category_id IS NULL THEN NULL ELSE c.name END AS category_name " +
                "FROM (transactions t LEFT JOIN categories c ON 1=1) " +
                "WHERE t.session_id = ? " +
                "AND (t.category_id IS NULL OR c.category_id = t.category_id)";
        String sessionID = headerSessionID != null ? headerSessionID : querySessionID;

        if (category != null) {
            transactionsQuery += "AND c.name = ?";
        }

        transactionsQuery += "LIMIT ? OFFSET ?;";

        List<Transaction> transactions = new ArrayList<>();

        try {

            Connection connection = DBConnection.instance.getConnection();

            PreparedStatement statement = connection.prepareStatement(transactionsQuery);
            statement.setString(1, sessionID);

            if (category != null) {
                statement.setString(2, category);
                statement.setInt(3, limit);
                statement.setInt(4, offset);
            } else {
                statement.setInt(2, limit);
                statement.setInt(3, offset);
            }

            ResultSet result = statement.executeQuery();
            while (result.next()) {
                Category resultCategory = null;
                int categoryId = result.getInt("category_id");
                if (!result.wasNull()) {
                    resultCategory = new Category(categoryId, result.getString("category_name"));
                }

                transactions.add(new Transaction(result.getInt("transaction_id"),
                        result.getString("date"),
                        result.getLong("amount"),
                        result.getString("description"),
                        result.getString("external_iban"),
                        Type.valueOf(result.getString("type")),
                        resultCategory
                        ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }

        response.setStatus(200);

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
        return gsonBuilder.create().toJson(transactions);
    }

    /**
     * Creates a new transaction that is linked to the current sessions id.
     * @param response to edit the status code of the response
     */
    @RequestMapping(value = "", method = RequestMethod.POST, produces = "application/json")
    public String createTransaction(@RequestHeader(value = "X-session-id", required = false) String headerSessionID,
                                         @RequestParam(value = "session_id", required = false) String querySessionID,
                                         @RequestBody String body,
                                         HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
            Gson gson = gsonBuilder.create();

            Transaction transaction = gson.fromJson(body, Transaction.class);

            if (transaction.getDate() == null || transaction.getAmount() == null || transaction.getExternalIBAN() ==
                    null || transaction.getType() == null) {
                throw new JsonSyntaxException("Transaction is missing attributes");
            }

            String query = "INSERT INTO transactions (date, amount, description, external_iban, category_id, type, session_id) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?);";
            String resultQuery = "SELECT last_insert_rowid() FROM transactions LIMIT 1;";

            try (Connection connection = DBConnection.instance.getConnection();
                 PreparedStatement resultStatement = connection.prepareStatement(resultQuery);
                 PreparedStatement statement = connection.prepareStatement(query)
            ) {
                statement.setString(1, transaction.getDate());
                statement.setDouble(2, transaction.getAmount());
                statement.setString(3, transaction.getDescription());
                statement.setString(4, transaction.getExternalIBAN());
                if (transaction.getCategory() != null) {
                    statement.setInt(5, transaction.getCategory().getId());
                }

                statement.setString(6, transaction.getType().toString().toLowerCase());
                statement.setString(7, sessionID);

                if (statement.executeUpdate() != 1) {
                    response.setStatus(405);
                    return null;
                }

                ResultSet result = resultStatement.executeQuery();

                if (result.next()) {
                    response.setStatus(201); // Set the status for getTransaction - getTransaction won't overwrite it.
                    return getTransaction(headerSessionID, querySessionID, result.getInt(1), response);
                }

                response.setStatus(405);
                return null;
            } catch (SQLException e) {
                e.printStackTrace();
                response.setStatus(500);
                return null;
            }
        } catch (JsonSyntaxException | NumberFormatException e) {
            e.printStackTrace();
            response.setStatus(405);
            return null;
        }
    }

    /**
     * Returns a specific transaction corresponding to the transaction id.
     * @param response to edit the status code of the response
     */
    @RequestMapping(value = "/{transactionId}", method = RequestMethod.GET, produces = "application/json")
    public String getTransaction(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                 @RequestParam(value = "session_id", required = false) String querySessionID,
                                 @PathVariable("transactionId") int transactionId,
                                 HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        String query = "SELECT DISTINCT t.transaction_id, t.date, t.amount, t.description, t.external_iban, t.type, " +
                "    CASE WHEN t.category_id IS NULL THEN NULL ELSE c.category_id END AS category_id, " +
                "    CASE WHEN t.category_id IS NULL THEN NULL ELSE c.name END AS category_name " +
                "FROM transactions t, categories c " +
                "WHERE t.session_id = ? " +
                "AND t.transaction_id = ? " +
                "AND (t.category_id IS NULL OR c.category_id = t.category_id)";

        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)
        ) {
            statement.setString(1, sessionID);
            statement.setInt(2, transactionId);
            ResultSet result = statement.executeQuery();
            if (result.next()) {
                // This check ensures that the status code is not overwritten if a different method has set it.
                // Other methods might use this methods to prevent code duplication when returning a Transaction.
                if (response.getStatus() != 201) {
                    response.setStatus(200);
                }

                Category category = null;
                int categoryId = result.getInt("category_id");
                if (!result.wasNull()) {
                    category = new Category(categoryId, result.getString("category_name"));
                }

                Transaction transaction = new Transaction(result.getInt("transaction_id"),
                        result.getString("date"),
                        result.getLong("amount"),
                        result.getString("description"),
                        result.getString("external_iban"),
                        Type.valueOf(result.getString("type")),
                        category
                );

                GsonBuilder gsonBuilder = new GsonBuilder();
                gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
                return gsonBuilder.create().toJson(transaction);
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
     * Updates the given transaction corresponding to the transaction id.
     * @param response to edit the status code of the response
     */
    @RequestMapping(value = "/{transactionId}", method = RequestMethod.PUT, produces = "application/json")
    public String updateTransaction(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                    @RequestParam(value = "session_id", required = false) String querySessionID,
                                    @PathVariable("transactionId") int transactionId,
                                    @RequestBody String body,
                                    HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
            Gson gson = gsonBuilder.create();

            Transaction transaction = gson.fromJson(body, Transaction.class);
            transaction.setId(transactionId);

            if (transaction.getDate() == null || transaction.getAmount() == null
                    || transaction.getExternalIBAN() == null || transaction.getType() == null) {
                throw new JsonSyntaxException("Transaction body is not formatted properly");
            }

            String query = "UPDATE transactions SET date = ?, amount = ?, description = ?, external_iban = ?, type = ? " +
                    "WHERE transaction_id = ? AND session_id = ?";

            try (Connection connection = DBConnection.instance.getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)
            ) {
                statement.setString(1, transaction.getDate());
                statement.setLong(2, transaction.getAmount());
                statement.setString(3, transaction.getDescription());
                statement.setString(4, transaction.getExternalIBAN());
                statement.setString(5, transaction.getType().toString().toLowerCase());
                statement.setInt(6, transactionId);
                statement.setString(7, sessionID);
                if (statement.executeUpdate() == 1) {
                    response.setStatus(200);
                    return getTransaction(headerSessionID, querySessionID, transactionId, response);
                } else {
                    response.setStatus(404);
                    return null;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.setStatus(500);
                return null;
            }
        } catch (JsonSyntaxException | NumberFormatException e) {
            e.printStackTrace();
            response.setStatus(405);
            return null;
        }
    }

    /**
     * Deletes the transaction corresponding to the given transaction id.
     * @param response to edit the status code of the response
     */
    @RequestMapping(value = "/{transactionId}", method = RequestMethod.DELETE)
    public void deleteTransaction(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @PathVariable("transactionId") int transactionId,
                                  HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;
        String query = "DELETE FROM transactions WHERE transaction_id = ? AND session_id = ?";
        DBUtil.executeDelete(response, query, transactionId, sessionID);
    }

    /**
     * Assigns a category to the specified transaction corresponding to the transaction id.
     * @param response to edit the status code of the response.
     */
    @RequestMapping(value = "/{transactionId}/category", method = RequestMethod.PATCH, produces = "application/json")
    public String assignCategoryToTransaction(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                              @RequestParam(value = "session_id", required = false) String querySessionID,
                                              @PathVariable("transactionId") int transactionId,
                                              @RequestBody String body,
                                              HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        int categoryId;
        try {
            categoryId = new Gson().fromJson(body, JsonObject.class).get("category_id").getAsInt();
        } catch (NullPointerException | NumberFormatException e) {
            // Body was not formatted according to API specification, treat as if no ID was specified.
            response.setStatus(404);
            return null;
        }

        String query = "UPDATE transactions SET category_id = ? WHERE transaction_id = ? AND session_id = ?";
        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)
        ) {
            statement.setInt(1, categoryId);
            statement.setInt(2, transactionId);
            statement.setString(3, sessionID);
            if (statement.executeUpdate() == 1) {
                return getTransaction(headerSessionID, querySessionID, transactionId, response);
            } else {
                response.setStatus(404);
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();

            // Since the error code is not set for this SQLException the message has to be used in order to find
            // out what the error was, in case of a foreign key constraint a 404 should be thrown instead of a 500.
            if (e.getMessage().startsWith("[SQLITE_CONSTRAINT]")) {
                response.setStatus(404);
                return null;
            }

            response.setStatus(500);
            return null;
        }
    }
}

class TransactionAdapter implements JsonDeserializer<Transaction>, JsonSerializer<Transaction> {

    @Override
    public Transaction deserialize(JsonElement json, java.lang.reflect.Type type,
                                   JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        String date = jsonObject.get("date").getAsString();
        Long amount = Long.valueOf(jsonObject.get("amount").getAsString().replace(".", ""));

        // Description is not present in earlier versions of the API so might be left out, check for null for safety.
        JsonElement descriptionElement = jsonObject.get("description");
        String description = (descriptionElement == null) ? null : descriptionElement.getAsString();

        String externalIBAN = jsonObject.get("externalIBAN").getAsString();
        Type transactionType = Type.valueOf(jsonObject.get("type").getAsString());
        Category category = null;

        if (json.getAsJsonObject().has("category")) {
            category = new Category(jsonObject.get("category").getAsJsonObject().get("id").getAsInt(),
                    jsonObject.get("category").getAsJsonObject().get("name").getAsString());
        }

        return new Transaction(null, date, amount, description, externalIBAN, transactionType, category);
    }

    @Override
    public JsonElement serialize(Transaction transaction, java.lang.reflect.Type type,
                                 JsonSerializationContext jsonSerializationContext) {
        JsonObject object = new JsonObject();

        object.addProperty("id", transaction.getId());
        object.addProperty("date", transaction.getDate());
        object.addProperty("amount", transaction.getAmount() / 100.0);

        if (transaction.getDescription() != null) {
            object.addProperty("description", transaction.getDescription());
        }

        object.addProperty("externalIBAN", transaction.getExternalIBAN());
        object.addProperty("type", transaction.getType().toString());

        if (transaction.getCategory() != null) {
            JsonObject categoryObject = new JsonObject();
            categoryObject.addProperty("id", transaction.getCategory().getId());
            categoryObject.addProperty("name", transaction.getCategory().getName());
            object.add("category", categoryObject);
        }

        return object;
    }
}
