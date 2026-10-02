package com.betterreads.features.session;

import com.betterreads.testsupport.Accounts;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

final class AuthRequests {

    private AuthRequests() {
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    static ResultActions register(final MockMvc mockMvc, final String body) throws Exception {
        return mockMvc.perform(post(Accounts.REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    static ResultActions login(final MockMvc mockMvc, final String body) throws Exception {
        return mockMvc.perform(post(Accounts.LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
