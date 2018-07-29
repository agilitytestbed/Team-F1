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

import nl.utwente.ing.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private static SessionService sessionService;

    @Autowired
    public SessionController(SessionService sessionService) {
        SessionController.sessionService = sessionService;
    }

    /**
     * Generates a new session ID to be used in future API requests as authentication.
     *
     * @param response the response shown to the user, necessary to edit the status code of the response
     * @return a JSON serialized representation of the generated session
     */
    @RequestMapping(value = "", method = RequestMethod.POST)
    public String getSession(HttpServletResponse response) {
        String sessionId = UUID.randomUUID().toString();

        while (sessionService.findBySessionID(sessionId) != null) {
            sessionId = UUID.randomUUID().toString();
        }

        sessionService.add(sessionId);

        response.setStatus(201);
        return String.format("{\"id\": \"%s\"}", sessionId);
    }

    /**
     * Checks whether a session is valid, i.e. not null and the session ID exists in the database.
     *
     * @param response  the response shown to the user, necessary to edit the status code of the response
     * @param sessionID the session ID for which to check validity
     * @return <code>true</code> if the session is not null and exists in the database; <code>false</code> otherwise
     */
    public static boolean isValidSession(HttpServletResponse response, String sessionID) {
        if (sessionID == null) {
            response.setStatus(401);
            return false;
        }

        if (sessionService.findBySessionID(sessionID) != null) {
            return true;
        } else {
            response.setStatus(401);
            return false;
        }
    }
}
