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
import nl.utwente.ing.model.Session;
import nl.utwente.ing.model.Transaction;
import nl.utwente.ing.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/balance/history")
public class BalanceHistoryController {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final TransactionService transactionService;

    @Autowired
    public BalanceHistoryController(TransactionService transactionService) {
        this.transactionService = transactionService;
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

        Calendar calendar = Calendar.getInstance();
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

        calendar.add(intervalType, count * -1);

        try {
            List<Transaction> transactions = transactionService.findBySessionAndDateAfterOrderByDateDesc(session, DATE_FORMAT.format(calendar.getTime()));
            long sum = transactionService.findBalanceBySessionAndDate(session, DATE_FORMAT.format(Calendar.getInstance().getTime()));
            long endSum = transactionService.findBalanceBySessionAndDate(session, DATE_FORMAT.format(calendar.getTime()));

            // Reset the calendar the the current date.
            calendar = Calendar.getInstance();

            // Set the reference calendar back one unit so we can find all transactions in that unit.
            calendar.add(intervalType, -1);

            List<HistoryItem> historyItems = new LinkedList<>();

            // Create an initial HistoryItem with the balance of the account as values.
            HistoryItem currentItem = new HistoryItem(sum, calendar.getTimeInMillis() / 1000);
            historyItems.add(currentItem);

            int groupCount = 0;

            // In case there are no transactions, fill the results with groups representing the sum and no movement.
            if (transactions.size() == 0) {
                while (historyItems.size() < count) {
                    calendar.add(intervalType, -1);
                    historyItems.add(new HistoryItem(sum, calendar.getTimeInMillis() / 1000));
                }
            }

            for (Transaction transaction : transactions) {
                // A calendar containing the date of the current transaction, used for comparing.
                Calendar pointer = Calendar.getInstance();
                pointer.setTime(DATE_FORMAT.parse(transaction.getDate()));

                while (calendar.after(pointer)) {
                    calendar.add(intervalType, -1);
                    currentItem = new HistoryItem(currentItem.getOpen(), calendar.getTimeInMillis() / 1000);
                    historyItems.add(currentItem);
                }

                long amount = transaction.getAmount();
                if (transaction.getType().equals(nl.utwente.ing.model.Type.withdrawal)) {
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
                    calendar.add(intervalType, -1);
                    currentItem = new HistoryItem(currentItem.getOpen(), calendar.getTimeInMillis() / 1000);
                    historyItems.add(currentItem);
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
                calendar.add(intervalType, -1);
                historyItems.add(new HistoryItem(endSum, calendar.getTimeInMillis() / 1000));
            }

            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(HistoryItem.class, new HistoryAdapter());
            return gsonBuilder.create().toJson(historyItems);
        } catch (ParseException e) {
            e.printStackTrace();
            response.setStatus(500);
            return null;
        }
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
