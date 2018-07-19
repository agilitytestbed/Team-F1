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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("api/v1/transactions")
public class TransactionController {

    /**
     * Returns a list of all the transactions that are available to the session ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param offset          the number of items to skip before starting to collect the result set
     * @param limit           the amount of items to return
     * @param category        the category used to filter the transactions
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of all transactions
     * @see Transaction
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getAllTransactions(@RequestHeader(value = "X-session-id", required = false) String headerSessionID,
                                     @RequestParam(value = "session_id", required = false) String querySessionID,
                                     @RequestParam(value = "offset", defaultValue = "0") int offset,
                                     @RequestParam(value = "limit", defaultValue = "20") int limit,
                                     @RequestParam(value = "category", required = false) String category,
                                     HttpServletResponse response) {
        String sessionID = headerSessionID != null ? headerSessionID : querySessionID;

        String transactionsQuery = "SELECT DISTINCT t.transaction_id, t.date, t.amount, t.description, t.external_iban, t.type, " +
                "CASE WHEN t.category_id IS NULL THEN NULL ELSE c.category_id END AS category_id, " +
                "CASE WHEN t.category_id IS NULL THEN NULL ELSE c.name END AS category_name " +
                "FROM (transactions t LEFT JOIN categories c ON 1=1) " +
                "WHERE t.session_id = ? " +
                "AND (t.category_id IS NULL OR c.category_id = t.category_id)";

        if (category != null) {
            transactionsQuery += "AND c.name = ?";
        }

        transactionsQuery += "LIMIT ? OFFSET ?;";

        List<Transaction> transactions = new ArrayList<>();

        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement statement = connection.prepareStatement(transactionsQuery)
        ) {
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

            response.setStatus(200);
            connection.commit();
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
            return gsonBuilder.create().toJson(transactions);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }
    }

    /**
     * Creates a new Transaction that is linked to the current session ID. Expects the body to be formatted according
     * to the <a href="https://app.swaggerhub.com/apis/djhuistra/INGHonours/1.2.1">API specification</a>.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param body            the request body containing the JSON representation of the Transaction to add
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the newly added Transaction
     * @see Transaction
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

            String query;
            if (transaction.getCategory() != null) {
                query = "INSERT INTO transactions (date, amount, description, external_iban, category_id, type, session_id) VALUES " +
                        "(?, ?, ?, ?, ?, ?, ?); ";
            } else {
                // In case no category was specified we check all of the category rules to see if one matches this
                // transaction and use the category_id associated with that category rule.
                query = "INSERT INTO transactions (date, amount, description, external_iban, category_id, type, session_id) VALUES " +
                        "(?, ?, ?, ?, (SELECT category_id " +
                        "FROM categoryrules " +
                        "WHERE (description = ? OR description = '') " +
                        "AND (iban = ? OR iban = '') " +
                        "AND (type = ? OR type = '') " +
                        "AND (category_id IS NOT NULL) " + // In case the category was removed.
                        "AND (session_id = ?) " +
                        "LIMIT 1), " +
                        "?, ?);";
            }

            // Checks whether the given category_id was available to the current user.
            String conditionQuery = "SELECT * FROM categories WHERE category_id = ? AND session_id = ?;";
            String resultQuery = "SELECT last_insert_rowid() FROM transactions LIMIT 1;";

            try (Connection connection = DBConnection.instance.getConnection();
                 PreparedStatement conditionStatement = connection.prepareStatement(conditionQuery);
                 PreparedStatement resultStatement = connection.prepareStatement(resultQuery);
                 PreparedStatement statement = connection.prepareStatement(query)
            ) {
                statement.setString(1, transaction.getDate());
                statement.setDouble(2, transaction.getAmount());
                statement.setString(3, transaction.getDescription());
                statement.setString(4, transaction.getExternalIBAN());
                if (transaction.getCategory() != null) {
                    // Checks whether the given category_id was available to the current user.
                    conditionStatement.setInt(1, transaction.getCategory().getId());
                    conditionStatement.setString(2, sessionID);
                    ResultSet conditionSet = conditionStatement.executeQuery();

                    if (!conditionSet.isBeforeFirst()) {
                        // We return 404 so users cannot tell whether a session exists or not when it is not owned by them.
                        response.setStatus(404);
                        connection.commit();
                        return null;
                    }

                    // The category belongs to the user, so we can continue execution.
                    statement.setInt(5, transaction.getCategory().getId());
                    statement.setString(6, transaction.getType().toString().toLowerCase());
                    statement.setString(7, sessionID);
                } else {
                    statement.setString(5, transaction.getDescription());
                    statement.setString(6, transaction.getExternalIBAN());
                    statement.setString(7, transaction.getType().toString().toLowerCase());
                    statement.setString(8, sessionID);
                    statement.setString(9, transaction.getType().toString().toLowerCase());
                    statement.setString(10, sessionID);
                }

                if (statement.executeUpdate() != 1) {
                    response.setStatus(405);
                    connection.rollback();
                    return null;
                }

                ResultSet result = resultStatement.executeQuery();

                if (result.next()) {
                    response.setStatus(201); // Set the status for getTransaction - getTransaction won't overwrite it.
                    connection.commit();
                    return getTransaction(headerSessionID, querySessionID, result.getInt(1), response);
                }

                response.setStatus(405);
                connection.rollback();
                return null;
            } catch (SQLException e) {
                e.printStackTrace();
                response.setStatus(500);
                return null;
            }
        } catch (JsonParseException | NumberFormatException e) {
            e.printStackTrace();
            response.setStatus(405);
            return null;
        }
    }

    /**
     * Returns a specific Transaction corresponding to the transaction ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param transactionID   the transaction ID corresponding to the transaction to return
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the specified Transaction
     * @see Transaction
     */
    @RequestMapping(value = "/{transactionId}", method = RequestMethod.GET, produces = "application/json")
    public String getTransaction(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                 @RequestParam(value = "session_id", required = false) String querySessionID,
                                 @PathVariable("transactionId") int transactionID,
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
            statement.setInt(2, transactionID);
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
                connection.commit();
                return gsonBuilder.create().toJson(transaction);
            } else {
                response.setStatus(404);
                connection.rollback();
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }
    }

    /**
     * Updates the given transaction corresponding to the transaction ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param transactionID   the transaction ID corresponding to the transaction to update
     * @param body            the request body containing the JSON representation of the Transaction to update
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the updated Transaction
     * @see Transaction
     */
    @RequestMapping(value = "/{transactionId}", method = RequestMethod.PUT, produces = "application/json")
    public String updateTransaction(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                    @RequestParam(value = "session_id", required = false) String querySessionID,
                                    @PathVariable("transactionId") int transactionID,
                                    @RequestBody String body,
                                    HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;

        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
            Gson gson = gsonBuilder.create();

            Transaction transaction = gson.fromJson(body, Transaction.class);
            transaction.setId(transactionID);

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
                statement.setInt(6, transactionID);
                statement.setString(7, sessionID);
                if (statement.executeUpdate() == 1) {
                    response.setStatus(200);
                    // This uses the getTransaction endpoint instead of just serializing the transaction object ..
                    // .. as not all fields are known at time of creation (e.g. the Category).
                    connection.commit();
                    return getTransaction(headerSessionID, querySessionID, transactionID, response);
                } else {
                    response.setStatus(404);
                    connection.rollback();
                    return null;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.setStatus(500);
                return null;
            }
        } catch (JsonParseException | NumberFormatException e) {
            e.printStackTrace();
            response.setStatus(405);
            return null;
        }
    }

    /**
     * Deletes the transaction corresponding to the given transaction ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param transactionID   the transaction ID corresponding to the transaction to delete
     * @param response        the response shown to the user, necessary to edit the status code of the response
     */
    @RequestMapping(value = "/{transactionId}", method = RequestMethod.DELETE)
    public void deleteTransaction(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @PathVariable("transactionId") int transactionID,
                                  HttpServletResponse response) {
        String sessionID = headerSessionID == null ? querySessionID : headerSessionID;
        String query = "DELETE FROM transactions WHERE transaction_id = ? AND session_id = ?";
        DBUtil.executeDelete(response, query, transactionID, sessionID);
    }

    /**
     * Assigns a category to the specified transaction corresponding to the transaction ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param transactionID   the transaction ID corresponding to the transaction to update
     * @param body            the request body containing the JSON representation of the Category to assign
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a serialized representation of the newly added Transaction
     * @see Transaction
     */
    @RequestMapping(value = "/{transactionId}/category", method = RequestMethod.PATCH, produces = "application/json")
    public String assignCategoryToTransaction(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                              @RequestParam(value = "session_id", required = false) String querySessionID,
                                              @PathVariable("transactionId") int transactionID,
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
        // Checks whether the given category_id was available to the current user.
        String conditionQuery = "SELECT * FROM categories WHERE category_id = ? AND session_id = ?;";

        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             PreparedStatement conditionStatement = connection.prepareStatement(conditionQuery)
        ) {
            // Checks whether the given category_id was available to the current user.
            conditionStatement.setInt(1, categoryId);
            conditionStatement.setString(2, sessionID);
            ResultSet conditionSet = conditionStatement.executeQuery();

            // In case there were no results, the category is not owned by the user or does not exist, so return 404.
            if (!conditionSet.isBeforeFirst()) {
                // We return 404 so users cannot tell whether a session exists or not when it is not owned by them.
                connection.commit();
                response.setStatus(404);
                return null;
            }

            statement.setInt(1, categoryId);
            statement.setInt(2, transactionID);
            statement.setString(3, sessionID);
            if (statement.executeUpdate() == 1) {
                connection.commit();
                return getTransaction(headerSessionID, querySessionID, transactionID, response);
            } else {
                response.setStatus(404);
                connection.commit();
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

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * A custom deserializer for GSON to use to deserialize a Transaction formatted according to the API specification
     * to Transaction object. Ensures that the amount field is properly converted to cents to work with the internally
     * used format.
     */
    @Override
    public Transaction deserialize(JsonElement json, java.lang.reflect.Type type,
                                   JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        JsonElement dateElement = jsonObject.get("date");
        JsonElement amountElement = jsonObject.get("amount");
        JsonElement typeElement = jsonObject.get("type");
        JsonElement ibanElement = jsonObject.get("externalIBAN");

        // Check whether all the fields are present to avoid NullPointerExceptions later on.
        if (dateElement == null || amountElement == null || typeElement == null || ibanElement == null) {
            throw new JsonParseException("Missing one or more required fields");
        }

        String date;
        try {
            date = dateElement.getAsString();
            // Check whether the date was formatted properly by parsing it.
            DATE_FORMAT.parse(date);
        } catch (ParseException e) {
            throw new JsonParseException("Invalid date specified");
        }

        Long amount = Long.valueOf(amountElement.getAsString().replace(".", ""));

        // Description is not present in earlier versions of the API so might be left out, check for null for safety.
        JsonElement descriptionElement = jsonObject.get("description");
        String description = (descriptionElement == null) ? null : descriptionElement.getAsString();

        String externalIBAN = ibanElement.getAsString();

        String typeString = typeElement.getAsString();
        if (!("deposit".equals(typeString) || "withdrawal".equals(typeString))) {
            throw new JsonParseException("Invalid type specified");
        }
        Type transactionType = Type.valueOf(typeString);

        Category category = null;
        if (json.getAsJsonObject().has("category")) {
            category = new Category(jsonObject.get("category").getAsJsonObject().get("id").getAsInt(),
                    jsonObject.get("category").getAsJsonObject().get("name").getAsString());
        }

        return new Transaction(null, date, amount, description, externalIBAN, transactionType, category);
    }

    /**
     * A custom serializer for GSON to use to serialize a Transaction into the proper JSON representation formatted
     * according to the API. Does not serialize null values unlike the default serializer. Formats the amount according
     * to the specification as they are internally stored in a long as cents.
     */
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
