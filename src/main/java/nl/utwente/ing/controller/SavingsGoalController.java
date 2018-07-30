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
import nl.utwente.ing.model.SavingsGoal;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.service.SavingsGoalService;
import nl.utwente.ing.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/savingGoals")
public class SavingsGoalController {

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
                                  @RequestParam(value = "session_id", required = false) String querySessionID) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);
        return new GsonBuilder().create().toJson(savingsGoalService.findBySession(session));
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
            Gson gson = new Gson();
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
            return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(savingsGoalService.add(savingsGoal));
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
