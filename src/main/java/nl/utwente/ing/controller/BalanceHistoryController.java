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
import nl.utwente.ing.model.HistoryItem;
import nl.utwente.ing.model.SavingsGoal;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.model.Transaction;
import nl.utwente.ing.service.SavingsGoalService;
import nl.utwente.ing.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/v1/balance/history")
public class BalanceHistoryController {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final TransactionService transactionService;
    private final SavingsGoalService savingsGoalService;

    @Autowired
    public BalanceHistoryController(TransactionService transactionService, SavingsGoalService savingsGoalService) {
        this.transactionService = transactionService;
        this.savingsGoalService = savingsGoalService;
    }

    /**
     * Returns the history of the balance of a bank account using candlestick datapoints. The result is formatted
     * according to the API specification: https://app.swaggerhub.com/apis/djhuistra/INGHonours-balanceHistory/
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param interval        the interval period, such as a week or month
     * @param count           the number of interval items to return
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the bank account's history.
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getBalanceHistory(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                    @RequestParam(value = "session_id", required = false) String querySessionID,
                                    @RequestParam(value = "interval", defaultValue = "month", required = false) String interval,
                                    @RequestParam(value = "intervals", defaultValue = "50", required = false) int count,
                                    HttpServletResponse response) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        // Intervals have a minimum of 1 and a maximum of 200.
        if (count < 1 || count > 200) {
            response.setStatus(405);
            return null;
        }

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.registerTypeAdapter(HistoryItem.class, new HistoryAdapter()).create();

        int intervalType;
        switch (interval) {
            case "hour":
                intervalType = Calendar.HOUR;
                break;
            case "day":
                intervalType = Calendar.DAY_OF_YEAR;
                break;
            case "week":
                intervalType = Calendar.WEEK_OF_YEAR;
                break;
            case "month":
                intervalType = Calendar.MONTH;
                break;
            case "year":
                intervalType = Calendar.YEAR;
                break;
            default:
                response.setStatus(405);
                return null;
        }

        try {
            List<Transaction> transactions = transactionService.findBySessionAsc(session);

            // In case there are no transactions, fill the results with empty groups.
            if (transactions.isEmpty()) {
                // Reset the date to the most recent transaction (now).
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(DATE_FORMAT.parse(transactionService.findFirstByOrderByDateDesc().getDate()));

                List<HistoryItem> historyItems = new LinkedList<>();
                while (historyItems.size() < count) {
                    calendar.add(intervalType, -1);
                    historyItems.add(new HistoryItem(0, calendar.getTimeInMillis() / 1000));
                }

                return gson.toJson(historyItems);
            }

            List<SavingsGoal> savingsGoals = savingsGoalService.findBySession(session);
            savingsGoals.forEach(s -> s.setBalance(0)); // Reset the balances of each goal so we can manually check whether they've been reached.

            // Track the volume of savings for each transaction so we can retrieve them easily later on.
            Map<Integer, Integer> savingVolumes = new HashMap<>();

            // For each transaction we set the balance at that time of the transaction - taking into account the savings.
            // This code is almost equal to the code in the SavingsGoalController, except we keep track of all states here.
            for (int i = 0; i < transactions.size() - 1; i++) {
                Transaction transaction = transactions.get(i);
                Transaction nextTransaction = transactions.get(i + 1);

                Calendar transactionDate = Calendar.getInstance();
                transactionDate.setTime(DATE_FORMAT.parse(transaction.getDate()));

                Calendar nextTransactionDate = Calendar.getInstance();
                nextTransactionDate.setTime(DATE_FORMAT.parse(nextTransaction.getDate()));

                if (transaction.getType().equals(nl.utwente.ing.model.Type.withdrawal)) {
                    transaction.setCurrentBalance(transaction.getCurrentBalance() - transaction.getAmount());
                } else {
                    transaction.setCurrentBalance(transaction.getCurrentBalance() + transaction.getAmount());
                }

                // The next transaction starts with the value this transaction ended with (plus savings).
                nextTransaction.setCurrentBalance(transaction.getCurrentBalance());

                int monthsPassed = monthsPassed(transactionDate, nextTransactionDate);

                for (int j = 0; j < monthsPassed; j++) {
                    for (SavingsGoal savingsGoal : savingsGoals) {
                        Calendar savingsGoalDate = Calendar.getInstance();
                        savingsGoalDate.setTime(DATE_FORMAT.parse(savingsGoal.getDate()));

                        // This savings goal is not valid yet.
                        if (transactionDate.before(savingsGoalDate)) {
                            continue;
                        }

                        // Ensure the requirements to process this saving have been met.
                        if (nextTransaction.getCurrentBalance() >= savingsGoal.getMinBalanceRequired() && savingsGoal.getBalance() < savingsGoal.getGoal()) {
                            nextTransaction.setCurrentBalance(nextTransaction.getCurrentBalance() - savingsGoal.getSavePerMonth());
                            savingsGoal.setBalance(savingsGoal.getBalance() + savingsGoal.getSavePerMonth());

                            if (savingVolumes.containsKey(nextTransaction.getId())) {
                                savingVolumes.put(nextTransaction.getId(), savingVolumes.get(nextTransaction.getId()) + savingsGoal.getSavePerMonth());
                            } else {
                                savingVolumes.put(nextTransaction.getId(), savingsGoal.getSavePerMonth());
                            }

                            // In case we went over the goal, we set the balance to the goal and "refund" the difference.
                            if (savingsGoal.getBalance() > savingsGoal.getGoal()) {
                                nextTransaction.setCurrentBalance(nextTransaction.getCurrentBalance() + (savingsGoal.getBalance() - savingsGoal.getGoal()));
                                savingsGoal.setBalance(savingsGoal.getGoal());

                                savingVolumes.put(nextTransaction.getId(), savingVolumes.get(nextTransaction.getId()) - (savingsGoal.getBalance() - savingsGoal.getGoal()));
                            }
                        }
                    }
                }
            }

            // The last transaction still needs to be processed because the loop above goes until size - 1.
            Transaction lastTransaction = transactions.get(transactions.size() - 1);
            if (lastTransaction.getType().equals(nl.utwente.ing.model.Type.withdrawal)) {
                lastTransaction.setCurrentBalance(lastTransaction.getCurrentBalance() - lastTransaction.getAmount());
            } else {
                lastTransaction.setCurrentBalance(lastTransaction.getCurrentBalance() + lastTransaction.getAmount());
            }

            // After processing the savings we can reverse the list, as balance history needs it in descending order.
            Collections.reverse(transactions);

            long balance = transactions.get(0).getCurrentBalance();

            // Reset the date to the most recent transaction (now).
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(DATE_FORMAT.parse(transactionService.findFirstByOrderByDateDesc().getDate()));

            // Set the reference calendar back one unit so we can find all transactions in that unit.
            calendar.add(intervalType, -1);

            List<HistoryItem> historyItems = new LinkedList<>();

            // Create an initial HistoryItem with the balance of the account as values.
            HistoryItem currentItem = new HistoryItem(balance, calendar.getTimeInMillis() / 1000);
            historyItems.add(currentItem);

            for (Transaction transaction : transactions) {
                balance = transaction.getCurrentBalance();

                // A calendar containing the date of the current transaction, used for comparing.
                Calendar pointer = Calendar.getInstance();
                pointer.setTime(DATE_FORMAT.parse(transaction.getDate()));

                while (calendar.after(pointer)) {
                    // Check whether we have already filled all the slots.
                    if (historyItems.size() >= count) {
                        break;
                    }

                    calendar.add(intervalType, -1);
                    currentItem = new HistoryItem(currentItem.getOpen(), calendar.getTimeInMillis() / 1000);
                    historyItems.add(currentItem);
                }

                if (pointer.before(calendar)) {
                    // We just entered a new group, we can stop adding to the old history item and create a new one.

                    // Stop once the maximum amount specified by the user has been reached.
                    if (historyItems.size() >= count) {
                        break;
                    }

                    // Some data from the old group has to be carried over to the new group.
                    calendar.add(intervalType, -1);
                    currentItem = new HistoryItem(currentItem.getOpen(), calendar.getTimeInMillis() / 1000);
                    historyItems.add(currentItem);
                }

                currentItem.setOpen(transaction.getCurrentBalance());

                if (currentItem.getOpen() > currentItem.getHigh()) {
                    currentItem.setHigh(currentItem.getOpen());
                }

                if (currentItem.getOpen() < currentItem.getLow()) {
                    currentItem.setLow(currentItem.getOpen());
                }

                currentItem.setVolume(
                        currentItem.getVolume() +
                        transaction.getAmount() +
                        savingVolumes.getOrDefault(transaction.getId(), 0)
                );
            }

            // In case not enough transactions were retrieved from the database to fill the count:
            // Similarly to the case of the empty set, create items of the last sum without any movement.
            while (historyItems.size() < count) {
                calendar.add(intervalType, -1);
                historyItems.add(new HistoryItem(balance, calendar.getTimeInMillis() / 1000));
            }

            return gson.toJson(historyItems);
        } catch (ParseException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }
    }

    private static int monthsPassed(Calendar start, Calendar end) {
        int yearDiff = end.get(Calendar.YEAR) - start.get(Calendar.YEAR);
        int monthDiff = end.get(Calendar.MONTH) - start.get(Calendar.MONTH);
        return Math.abs((yearDiff * 12) + monthDiff);
    }
}

class HistoryAdapter implements JsonSerializer<HistoryItem> {

    /**
     * A custom serializer for GSON to use to serialize a HistoryItem into the proper JSON representation formatted
     * according to the API. Formats the monetary values according to the specification as they are internally
     * stored in a long as cents.
     */
    @Override
    public JsonElement serialize(HistoryItem historyItem, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject object = new JsonObject();
        // Formats the values in the database according to the API specification.
        object.addProperty("open", historyItem.getOpen() / 100.0);
        object.addProperty("close", historyItem.getClose() / 100.0);
        object.addProperty("high", historyItem.getHigh() / 100.0);
        object.addProperty("low", historyItem.getLow() / 100.0);
        object.addProperty("volume", historyItem.getVolume() / 100.0);
        object.addProperty("timestamp", historyItem.getTimestamp());
        return object;
    }
}
