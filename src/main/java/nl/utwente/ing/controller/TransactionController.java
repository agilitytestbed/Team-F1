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
import nl.utwente.ing.model.Category;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.model.Transaction;
import nl.utwente.ing.model.Type;
import nl.utwente.ing.service.CategoryService;
import nl.utwente.ing.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;

@RestController
@RequestMapping("api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    @Autowired
    public TransactionController(TransactionService transactionService, CategoryService categoryService) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;
    }

    /**
     * Returns a list of all the transactions that are available to the session ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param offset          the number of items to skip before starting to collect the result set
     * @param limit           the amount of items to return
     * @param categoryName    the category used to filter the transactions
     * @return a JSON serialized representation of all transactions
     * @see Transaction
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getAllTransactions(@RequestHeader(value = "X-session-id", required = false) String headerSessionID,
                                     @RequestParam(value = "session_id", required = false) String querySessionID,
                                     @RequestParam(value = "offset", defaultValue = "0") int offset,
                                     @RequestParam(value = "limit", defaultValue = "20") int limit,
                                     @RequestParam(value = "category", required = false) String categoryName) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());

        if (categoryName != null) {
            return gsonBuilder.create().toJson(transactionService.findBySessionAndCategoryName(session, categoryName, offset, limit));
        } else {
            return gsonBuilder.create().toJson(transactionService.findBySession(session, offset, limit));
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
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
            Gson gson = gsonBuilder.create();

            Transaction transaction = gson.fromJson(body, Transaction.class);
            transaction.setSession(session);

            if (transaction.getDate() == null || transaction.getAmount() == null || transaction.getExternalIBAN() ==
                    null || transaction.getType() == null) {
                throw new JsonSyntaxException("Transaction is missing attributes");
            }

            response.setStatus(201);
            return gsonBuilder.create().toJson(transactionService.add(transaction));
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
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());

        Transaction result = transactionService.findByIdAndSession(transactionID, session);
        response.setStatus(result == null ? 404 : 200);
        return gsonBuilder.create().toJson(result);
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
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
            Gson gson = gsonBuilder.create();

            Transaction transaction = gson.fromJson(body, Transaction.class);
            transaction.setId(transactionID);
            transaction.setSession(session);

            if (transaction.getDate() == null || transaction.getAmount() == null
                    || transaction.getExternalIBAN() == null || transaction.getType() == null) {
                throw new JsonSyntaxException("Transaction body is not formatted properly");
            }

            if (transactionService.update(transaction) == 1) {
                return getTransaction(headerSessionID, querySessionID, transactionID, response);
            } else {
                response.setStatus(404);
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
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);
        response.setStatus(transactionService.delete(transactionID, session) == 1 ? 204 : 404);
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
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        try {
            int categoryId = new Gson().fromJson(body, JsonObject.class).get("category_id").getAsInt();
            Transaction transaction = transactionService.findByIdAndSession(transactionID, session);
            Category category = categoryService.findByIdAndSession(categoryId, session);

            if (category == null || transaction == null) {
                response.setStatus(404);
                return null;
            }

            transactionService.updateCategory(transaction, category);
            return getTransaction(headerSessionID, querySessionID, transactionID, response);
        } catch (NullPointerException | NumberFormatException e) {
            // Body was not formatted according to API specification, treat as if no ID was specified.
            response.setStatus(404);
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
