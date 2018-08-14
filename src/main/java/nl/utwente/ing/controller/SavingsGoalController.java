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
import nl.utwente.ing.model.SavingsGoal;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.model.Transaction;
import nl.utwente.ing.model.Type;
import nl.utwente.ing.service.SavingsGoalService;
import nl.utwente.ing.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

@RestController
@RequestMapping("/api/v1/savingGoals")
public class SavingsGoalController {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final SavingsGoalService savingsGoalService;
    private final TransactionService transactionService;

    @Autowired
    public SavingsGoalController(SavingsGoalService savingsGoalService, TransactionService transactionService) {
        this.savingsGoalService = savingsGoalService;
        this.transactionService = transactionService;
    }

    /**
     * Returns a list of all the savings goals defined by the current session ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID the session ID present in the URL of the request
     * @return a JSON serialized representation of all savings goals
     * @see SavingsGoal
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getSavingsGoals(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  HttpServletResponse response) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        List<Transaction> transactions = transactionService.findBySessionAsc(session);
        List<SavingsGoal> savingsGoals = savingsGoalService.findBySession(session);

        long balance = 0;

        try {
            for (int i = 0; i < transactions.size() - 1; i++) {
                Transaction transaction = transactions.get(i);
                Calendar transactionDate = Calendar.getInstance();
                transactionDate.setTime(DATE_FORMAT.parse(transaction.getDate()));

                if (transaction.getType().equals(Type.withdrawal)) {
                    balance -= transaction.getAmount();
                } else {
                    balance += transaction.getAmount();
                }

                int monthsPassed = monthsPassed(transaction, transactions.get(i + 1));

                for (int j = 0; j < monthsPassed; j++) {
                    for (SavingsGoal savingsGoal : savingsGoals) {
                        Calendar savingsGoalDate = Calendar.getInstance();
                        savingsGoalDate.setTime(DATE_FORMAT.parse(savingsGoal.getDate()));

                        // This savings goal is not valid yet.
                        if (transactionDate.before(savingsGoalDate)) {
                            continue;
                        }

                        if (balance >= savingsGoal.getMinBalanceRequired() && savingsGoal.getBalance() < savingsGoal.getGoal()) {
                            balance -= savingsGoal.getSavePerMonth();
                            savingsGoal.setBalance(savingsGoal.getBalance() + savingsGoal.getSavePerMonth());

                            // In case we went over the goal, we set the balance to the goal and "refund" the difference.
                            if (savingsGoal.getBalance() > savingsGoal.getGoal()) {
                                balance += (savingsGoal.getBalance() - savingsGoal.getGoal());
                                savingsGoal.setBalance(savingsGoal.getGoal());
                            }
                        }
                    }
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }

        return new GsonBuilder()
                .registerTypeAdapter(SavingsGoal.class, new SavingsGoalAdapter())
                .excludeFieldsWithoutExposeAnnotation()
                .create()
                .toJson(savingsGoals);
    }

    private static int monthsPassed(Transaction first, Transaction second) throws ParseException {
        Calendar start = Calendar.getInstance();
        start.setTime(DATE_FORMAT.parse(first.getDate()));

        Calendar end = Calendar.getInstance();
        end.setTime(DATE_FORMAT.parse(second.getDate()));

        int yearDiff = end.get(Calendar.YEAR) - start.get(Calendar.YEAR);
        int monthDiff = end.get(Calendar.MONTH) - start.get(Calendar.MONTH);
        return Math.abs((yearDiff * 12) + monthDiff);
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
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(SavingsGoal.class, new SavingsGoalAdapter());
            Gson gson = gsonBuilder.create();

            SavingsGoal savingsGoal = gson.fromJson(body, SavingsGoal.class);
            savingsGoal.setSession(session);
            savingsGoal.setBalance(0);
            // Set the date to the most recent transaction (now).
            savingsGoal.setDate(transactionService.findFirstByOrderByDateDesc().getDate());

            if (savingsGoal.getName() == null || savingsGoal.getGoal() == null
                    || savingsGoal.getSavePerMonth() == null) {
                throw new JsonSyntaxException("SavingsGoal is missing one or more elements");
            }

            response.setStatus(201);
            return new GsonBuilder()
                    .registerTypeAdapter(SavingsGoal.class, new SavingsGoalAdapter())
                    .excludeFieldsWithoutExposeAnnotation()
                    .create()
                    .toJson(savingsGoalService.add(savingsGoal));
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
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);
        response.setStatus(savingsGoalService.delete(id, session) == 1 ? 204 : 404);
    }
}

class SavingsGoalAdapter implements JsonDeserializer<SavingsGoal>, JsonSerializer<SavingsGoal> {

    /**
     * A custom deserializer for GSON to use to deserialize a SavingsGoal formatted according to the API specification
     * to SavingsGoal object. Ensures that the amount field is properly converted to cents to work with the internally
     * used format.
     */
    @Override
    public SavingsGoal deserialize(JsonElement json, java.lang.reflect.Type type,
                                   JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        JsonElement nameElement = jsonObject.get("name");
        JsonElement goalElement = jsonObject.get("goal");
        JsonElement monthElement = jsonObject.get("savePerMonth");
        JsonElement minBalanceElement = jsonObject.get("minBalanceRequired");

        // Check whether all the fields are present to avoid NullPointerExceptions later on.
        if (nameElement == null || goalElement == null || monthElement == null || minBalanceElement == null) {
            throw new JsonParseException("Missing one or more required fields");
        }

        String name = nameElement.getAsString();

        Integer goal;
        if (goalElement.getAsString().contains(".")) {
            goal = Integer.valueOf(goalElement.getAsString().replace(".", ""));
        } else {
            goal = Integer.valueOf(goalElement.getAsString()) * 100;
        }

        Integer month;
        if (monthElement.getAsString().contains(".")) {
            month = Integer.valueOf(monthElement.getAsString().replace(".", ""));
        } else {
            month = Integer.valueOf(monthElement.getAsString()) * 100;
        }

        Integer minBalance;
        if (minBalanceElement.getAsString().contains(".")) {
            minBalance = Integer.valueOf(minBalanceElement.getAsString().replace(".", ""));
        } else {
            minBalance = Integer.valueOf(minBalanceElement.getAsString()) * 100;
        }

        SavingsGoal savingsGoal = new SavingsGoal();
        savingsGoal.setName(name);
        savingsGoal.setGoal(goal);
        savingsGoal.setSavePerMonth(month);
        savingsGoal.setMinBalanceRequired(minBalance);
        return savingsGoal;
    }

    /**
     * A custom serializer for GSON to use to serialize a Transaction into the proper JSON representation formatted
     * according to the API. Does not serialize null values unlike the default serializer. Formats the amount according
     * to the specification as they are internally stored in a long as cents.
     */
    @Override
    public JsonElement serialize(SavingsGoal savingsGoal, java.lang.reflect.Type type,
                                 JsonSerializationContext jsonSerializationContext) {
        JsonObject object = new JsonObject();

        object.addProperty("id", savingsGoal.getId());
        object.addProperty("name", savingsGoal.getName());
        object.addProperty("goal", savingsGoal.getGoal() / 100.0);
        object.addProperty("savePerMonth", savingsGoal.getSavePerMonth() / 100.0);
        object.addProperty("minBalanceRequired", savingsGoal.getMinBalanceRequired() / 100.0);
        object.addProperty("balance", savingsGoal.getBalance() / 100.0);

        return object;
    }
}
