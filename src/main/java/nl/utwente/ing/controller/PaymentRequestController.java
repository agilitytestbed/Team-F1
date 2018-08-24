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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import javax.servlet.http.HttpServletResponse;
import nl.utwente.ing.model.PaymentRequest;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.model.Transaction;
import nl.utwente.ing.service.PaymentRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/paymentRequests")
public class PaymentRequestController {

    private static PaymentRequestService paymentRequestService;

    @Autowired
    public PaymentRequestController(PaymentRequestService paymentRequestService) {
        PaymentRequestController.paymentRequestService = paymentRequestService;
    }

    /**
     * Returns a list of all the payment requests that are available to the session ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @return a JSON serialized representation of all payment requests
     * @see PaymentRequest
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getPaymentRequests(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                     @RequestParam(value = "session_id", required = false) String querySessionID) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(PaymentRequest.class, new PaymentRequestAdapter());

        return gsonBuilder.create().toJson(paymentRequestService.findBySession(session));
    }

    /**
     * Creates a new PaymentRequest that is linked to the current session ID. Expects the body to be formatted according
     * to the <a href="https://app.swaggerhub.com/apis/djhuistra/INGHonours-PaymentRequest/">API specification</a>.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param body            the request body containing the JSON representation of the PaymentRequest to add
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the newly added PaymentRequest
     * @see PaymentRequest
     */
    @RequestMapping(value = "", method = RequestMethod.POST, produces = "application/json")
    public String addPaymentRequest(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                    @RequestParam(value = "session_id", required = false) String querySessionID,
                                    @RequestBody String body,
                                    HttpServletResponse response) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(PaymentRequest.class, new PaymentRequestAdapter());
            Gson gson = gsonBuilder.create();

            PaymentRequest paymentRequest = gson.fromJson(body, PaymentRequest.class);
            paymentRequest.setSession(session);

            if (paymentRequest.getDescription() == null || paymentRequest.getRequestCount() == 0
                    || paymentRequest.getDueDate() == null || paymentRequest.getAmount() == 0) {
                throw new JsonSyntaxException("PaymentRequest is missing one or more elements");
            }

            response.setStatus(201);
            return gsonBuilder.create().toJson(paymentRequestService.add(paymentRequest));
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            response.setStatus(405);
            return null;
        }
    }
}

class PaymentRequestAdapter implements JsonDeserializer<PaymentRequest>, JsonSerializer<PaymentRequest> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * A custom deserializer for GSON to use to deserialize a PaymentRequest formatted according to the API specification
     * to a PaymentRequest object. Ensures that the amount field is properly converted to cents to work with the internally
     * used format.
     */
    @Override
    public PaymentRequest deserialize(JsonElement json, java.lang.reflect.Type type,
                                      JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        JsonElement descriptionElement = jsonObject.get("description");
        JsonElement amountElement = jsonObject.get("amount");
        JsonElement dueDateElement = jsonObject.get("due_date");
        JsonElement requestCountElement = jsonObject.get("number_of_requests");

        // Check whether all the fields are present to avoid NullPointerExceptions later on.
        if (descriptionElement == null || amountElement == null || dueDateElement == null || requestCountElement == null) {
            throw new JsonParseException("Missing one or more required fields");
        }

        String dueDate;
        try {
            dueDate = dueDateElement.getAsString();
            // Check whether the date was formatted properly by parsing it.
            DATE_FORMAT.parse(dueDate);
        } catch (ParseException e) {
            throw new JsonParseException("Invalid date specified");
        }

        long amount;
        if (amountElement.getAsString().contains(".")) {
            amount = Long.parseLong(amountElement.getAsString().replace(".", ""));
        } else {
            amount = Long.valueOf(amountElement.getAsString()) * 100;
        }

        String description = descriptionElement.getAsString();
        int requestCount = requestCountElement.getAsInt();

        return new PaymentRequest(description, dueDate, amount, requestCount);
    }

    /**
     * A custom serializer for GSON to use to serialize a PaymentRequest into the proper JSON representation formatted
     * according to the API. Formats the amount according to the specification as they are internally stored in a
     * long as cents.
     */
    @Override
    public JsonElement serialize(PaymentRequest paymentRequest, java.lang.reflect.Type type,
                                 JsonSerializationContext jsonSerializationContext) {
        JsonObject object = new JsonObject();

        object.addProperty("id", paymentRequest.getId());
        object.addProperty("description", paymentRequest.getDescription());
        object.addProperty("due_date", paymentRequest.getDueDate());
        object.addProperty("amount", paymentRequest.getAmount() / 100.0);
        object.addProperty("number_of_requests", paymentRequest.getRequestCount());
        object.addProperty("filled", paymentRequest.isFilled());

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Transaction.class, new TransactionAdapter());
        Gson gson = gsonBuilder.create();

        object.add("transactions", gson.toJsonTree(paymentRequest.getTransactions()));

        return object;
    }
}
