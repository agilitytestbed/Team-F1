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

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import nl.utwente.ing.model.Session;
import nl.utwente.ing.model.UserMessage;
import nl.utwente.ing.service.UserMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
public class UserMessageController {

    private final UserMessageService userMessageService;

    @Autowired
    public UserMessageController(UserMessageService userMessageService) {
        this.userMessageService = userMessageService;
    }

    /**
     * Returns a list of all unread user messages available to the session ID.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @return a JSON serialized representation of all unread user messages
     * @see UserMessage
     */
    @RequestMapping(value = "", method = RequestMethod.GET, produces = "application/json")
    public List<UserMessage> getUserMessages(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                  @RequestParam(value = "session_id", required = false) String querySessionID) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);
        return userMessageService.findUnreadBySession(session);
    }

    /**
     * Marks the message corresponding to the ID as read.
     *
     * @param headerSessionID the session ID present in the header of the request
     * @param querySessionID  the session ID present in the URL of the request
     * @param id              the message ID corresponding to the user message to update
     * @param response        the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the updated UserMessage
     * @see UserMessage
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public UserMessage putUserMessage(@RequestHeader(value = "X-session-ID", required = false) String headerSessionID,
                                      @RequestParam(value = "session_id", required = false) String querySessionID,
                                      @PathVariable("id") int id,
                                      HttpServletResponse response) {
        Session session = new Session(headerSessionID == null ? querySessionID : headerSessionID);

        UserMessage userMessage = userMessageService.findBySessionAndId(session, id);

        if (userMessage == null) {
            response.setStatus(404);
            return null;
        }

        userMessage.setRead(true);
        return userMessageService.add(userMessage);
    }
}
