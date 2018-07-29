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
import nl.utwente.ing.model.Category;
import nl.utwente.ing.model.CategoryRule;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.service.CategoryRuleService;
import nl.utwente.ing.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categoryRules")
public class CategoryRuleController {

    private final CategoryRuleService categoryRuleService;
    private final CategoryService categoryService;

    @Autowired
    public CategoryRuleController(CategoryRuleService categoryRuleService, CategoryService categoryService) {
        this.categoryRuleService = categoryRuleService;
        this.categoryService = categoryService;
    }

    /**
     * Returns a list of all the category rules that are available to the session ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @return a JSON serialized representation of all category rules
     * @see CategoryRule
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public String getCategoryRules(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                   @RequestParam(value = "session_id", required = false) String querySessionID) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        List<CategoryRule> categoryRules = categoryRuleService.findBySession(session);
        return new GsonBuilder().create().toJson(categoryRules);
    }

    /**
     * Creates a new CategoryRule that is linked to the current session ID. Expects the body to be formatted according
     * to the <a href="https://app.swaggerhub.com/apis/djhuistra/INGHonours-CategoryRules/">API specification</a>.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param body            the request body containing the JSON representation of the CategoryRule to add
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the newly added CategoryRule
     * @see CategoryRule
     */
    @RequestMapping(value = "", method = RequestMethod.POST, produces = "application/json")
    public String addCategoryRule(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @RequestBody String body,
                                  HttpServletResponse response) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        try {
            Gson gson = new Gson();
            CategoryRule categoryRule = gson.fromJson(body, CategoryRule.class);

            if (categoryRule.getDescription() == null || categoryRule.getIban() == null
                    || categoryRule.getType() == null || categoryRule.getCategoryId() == null) {
                throw new JsonSyntaxException("CategoryRule is missing one or more elements");
            }

            Category category = categoryService.findByIdAndSession(categoryRule.getCategoryId(), session);

            if (category == null) {
                response.setStatus(404);
                return null;
            }

            categoryRule.setSession(session);
            categoryRule.setCategory(category);
            CategoryRule result = categoryRuleService.add(categoryRule);

            if (result.shouldApplyOnHistory()) {
                categoryRuleService.updateTransactions(result);
            }

            response.setStatus(201);
            return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(result);
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
     * @param querySessionID  the session ID present in the URL of the request
     * @param id              the category rule ID corresponding to the category rule to return
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the specified CategoryRule
     * @see CategoryRule
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = "application/json")
    public String getCategoryRule(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @PathVariable("id") int id,
                                  HttpServletResponse response) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        CategoryRule result = categoryRuleService.findByIdAndSession(id, session);

        if (result == null) {
            response.setStatus(404);
            return null;
        } else {
            response.setStatus(200);
            result.setCategoryId(result.getCategory().getId());
            return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(result);
        }
    }

    /**
     * Updates the given category rule corresponding to the category rule ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param id              the category rule ID corresponding to the category rule to update
     * @param body            the request body containing the JSON representation of the CategoryRule to update
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the updated CategoryRule
     * @see CategoryRule
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, produces = "application/json")
    public String putCategoryRule(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID,
                                  @PathVariable("id") int id,
                                  @RequestBody String body,
                                  HttpServletResponse response) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        try {
            Gson gson = new Gson();
            CategoryRule categoryRule = gson.fromJson(body, CategoryRule.class);
            categoryRule.setId(id);

            if (categoryRule.getDescription() == null || categoryRule.getIban() == null
                    || categoryRule.getType() == null || categoryRule.getId() == null) {
                throw new JsonSyntaxException("CategoryRule is missing one or more elements");
            }

            Category category = categoryService.findByIdAndSession(categoryRule.getCategoryId(), session);

            if (category == null) {
                response.setStatus(404);
                return null;
            }

            categoryRule.setSession(session);
            categoryRule.setCategory(category);

            if (categoryRuleService.update(categoryRule) == 1) {
                return getCategoryRule(headerSessionID, querySessionID, id, response);
            } else {
                response.setStatus(404);
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
     * @param querySessionID  the session ID present in the URL of the request
     * @param id              the category rule ID corresponding to the category to delete
     * @param response        the response shown to the user, necessary to edit the status code of the response
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void deleteCategoryRule(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                   @RequestParam(value = "session_id", required = false) String querySessionID,
                                   @PathVariable("id") int id,
                                   HttpServletResponse response) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);
        response.setStatus(categoryRuleService.delete(id, session) == 1 ? 204 : 404);
    }
}
