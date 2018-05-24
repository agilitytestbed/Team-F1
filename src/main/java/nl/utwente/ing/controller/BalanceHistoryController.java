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
import nl.utwente.ing.model.HistoryItem;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/v1/balance/history")
public class BalanceHistoryController {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getBalanceHistory(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                    @RequestParam(value = "session_id", required = false) String querySessionID,
                                    @RequestParam(value = "interval", defaultValue = "month", required = false) String interval,
                                    @RequestParam(value = "intervals", defaultValue = "50", required = false) int count,
                                    HttpServletResponse response) {
        String sessionID = headerSessionID != null ? headerSessionID : querySessionID;

        // Intervals have a minimum of 1 and a maximum of 200.
        if (count < 1 || count > 200) {
            response.setStatus(405);
            return null;
        }

        Calendar calendar = Calendar.getInstance();
        int intervalType;
        switch (interval) {
            case "hour":    intervalType = Calendar.HOUR;
                break;
            case "day":     intervalType = Calendar.DAY_OF_YEAR;
                break;
            case "week":    intervalType = Calendar.WEEK_OF_YEAR;
                break;
            case "month":   intervalType = Calendar.MONTH;
                break;
            case "year":    intervalType = Calendar.YEAR;
                break;
            default:        response.setStatus(405);
                return null;
        }

        // Set the calendar to the date of the earliest transaction possible.
        // This is done so we don't fetch transactions that are not included in the history anyway.
        calendar.add(intervalType, count * -1);

        // Select all transactions for the current user, starting at the earliest transaction.
        String query = "SELECT DISTINCT date, amount, type " +
                "FROM transactions " +
                "WHERE session_id = ? " +
                "AND date > ? " +
                "ORDER BY date DESC";

        // Get the close of the last group, which is effectively the current balance of the account.
        String sumQuery = "SELECT (total_pos.total - total_neg.total) " +
                "FROM (" +
                "    (SELECT COALESCE(SUM(dep.amount), 0) AS total FROM transactions dep WHERE dep.type = \"deposit\" AND session_id = ? AND date < ?) AS total_pos,  " +
                "    (SELECT COALESCE(SUM(with.amount), 0) AS total FROM transactions with WHERE with.type = \"withdrawal\" AND session_id = ? AND date < ?) AS total_neg " +
                ")";

        try (Connection connection = DBConnection.instance.getConnection();
             PreparedStatement transactionsStatement = connection.prepareStatement(query);
             PreparedStatement sumStatement = connection.prepareStatement(sumQuery);
             PreparedStatement endSumStatement = connection.prepareStatement(sumQuery)
        ){
            // Setting up the transaction statement
            transactionsStatement.setString(1, sessionID);
            transactionsStatement.setString(2, DATE_FORMAT.format(calendar.getTime()));
            // Setting up the sum statement
            sumStatement.setString(1, sessionID);
            sumStatement.setString(2, DATE_FORMAT.format(Calendar.getInstance().getTime()));
            sumStatement.setString(3, sessionID);
            sumStatement.setString(4, DATE_FORMAT.format(Calendar.getInstance().getTime()));
            // Setting up the end sum statement
            endSumStatement.setString(1, sessionID);
            endSumStatement.setString(2, DATE_FORMAT.format(calendar.getTime()));
            endSumStatement.setString(3, sessionID);
            endSumStatement.setString(4, DATE_FORMAT.format(calendar.getTime()));

            ResultSet transactionsSet = transactionsStatement.executeQuery();
            ResultSet sumSet = sumStatement.executeQuery();
            ResultSet endSumSet = endSumStatement.executeQuery();

            long sum = 0;
            while (sumSet.next()) {
                sum = sumSet.getLong(1);
            }

            long endSum = 0;
            while (endSumSet.next()) {
                endSum = endSumSet.getLong(1);
            }

            // Reset the calendar the the current date.
            calendar = Calendar.getInstance();

            // Set the reference calendar back one unit so we can find all transactions in that unit.
            calendar.add(intervalType, -1);

            List<HistoryItem> historyItems = new LinkedList<>();

            // Create an initial HistoryItem with the balance of the account as values.
            HistoryItem currentItem = new HistoryItem(sum);
            historyItems.add(currentItem);

            int groupCount = 0;

            // In case there are no transactions, fill the results with groups representing the sum and no movement.
            if (transactionsSet.isAfterLast()) {
                while (historyItems.size() < count) {
                    historyItems.add(new HistoryItem(sum));
                }
            }

            while (transactionsSet.next()) {
                // A calendar containing the date of the current transaction, used for comparing.
                Calendar pointer = Calendar.getInstance();
                pointer.setTime(DATE_FORMAT.parse(transactionsSet.getString("date")));

                while (calendar.after(pointer)) {
                    currentItem = new HistoryItem(currentItem.getOpen());
                    historyItems.add(currentItem);
                    calendar.add(intervalType, -1);
                }

                long amount = transactionsSet.getLong("amount");
                if ("withdrawal".equals(transactionsSet.getString("type"))) {
                    // Negate amount in case it was a withdrawal.
                    amount = amount * -1;
                }

                if (pointer.before(calendar)) {
                    // We just entered a new group, we can stop adding to the old history item and create a new one.
                    groupCount++;

                    // Stop once the maximum amount specified by the user has been reached.
                    if (groupCount >= count) {
                        break;
                    }

                    // Some data from the old group has to be carried over to the new group.
                    currentItem = new HistoryItem(currentItem.getOpen());
                    historyItems.add(currentItem);
                    calendar.add(intervalType, -1);
                }

                // Update the open value, which is the "current" value.
                currentItem.setOpen(currentItem.getOpen() - amount);

                if (currentItem.getOpen() > currentItem.getHigh()) {
                    currentItem.setHigh(currentItem.getOpen());
                }

                if (currentItem.getOpen() < currentItem.getLow()) {
                    currentItem.setLow(currentItem.getOpen());
                }

                currentItem.setVolume(currentItem.getVolume() + Math.abs(amount));
            }

            // In case not enough transactions were retrieved from the database to fill the count:
            // Similarly to the case of the empty set, create items of the last sum without any movement.
            while (historyItems.size() < count) {
                historyItems.add(new HistoryItem(endSum));
            }

            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(HistoryItem.class, new HistoryAdapter());
            return gsonBuilder.create().toJson(historyItems);
        } catch (SQLException | ParseException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }
    }
}

class HistoryAdapter implements JsonSerializer<HistoryItem> {

    @Override
    public JsonElement serialize(HistoryItem historyItem, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject object = new JsonObject();
        // Formats the values in the database according to the API specification.
        object.addProperty("open", historyItem.getOpen() / 100.0);
        object.addProperty("close", historyItem.getClose() / 100.0);
        object.addProperty("high", historyItem.getHigh() / 100.0);
        object.addProperty("low", historyItem.getLow() / 100.0);
        object.addProperty("volume", historyItem.getVolume() / 100.0);
        return object;
    }
}
