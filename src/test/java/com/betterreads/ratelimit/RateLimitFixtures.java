package com.betterreads.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

// PMD.AvoidUsingHardCodedIP: client IPs are fixtures from the 203.0.113.0/24 documentation range.
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
final class RateLimitFixtures {

    static final String LOGIN_URL = "/api/v1/auth/login";

    static final int LOGIN_BURST = 10;

    static final String CLIENT_A = "203.0.113.10";

    static final String CLIENT_B = "203.0.113.20";

    static final String PROXY = "203.0.113.1";

    static final String PROXY_CIDR = "203.0.113.1/32";

    static final String XFF_HEADER = "X-Forwarded-For";

    static final String CF_CONNECTING_IP_HEADER = "CF-Connecting-IP";

    static final String RETRY_AFTER_HEADER = "Retry-After";

    private RateLimitFixtures() {
    }

    static String loginPayload(final ObjectMapper objectMapper) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("identifier", "ghost");
        node.put("password", "anything-not-matching");
        return objectMapper.writeValueAsString(node);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    static void assertSeparateBucketsPerClient(
        final MockMvc mockMvc, final String body, final String clientIpHeader) throws Exception {
        for (int i = 0; i < LOGIN_BURST; i++) {
            mockMvc.perform(login(body).header(clientIpHeader, CLIENT_A))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(body).header(clientIpHeader, CLIENT_A))
            .andExpect(status().isTooManyRequests());

        mockMvc.perform(login(body).header(clientIpHeader, CLIENT_B))
            .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder login(final String body) {
        return post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
