package com.betterreads.testsupport;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public final class Comments {

    private Comments() {
    }

    public static String reviewCommentsUrl(final long reviewId) {
        return "/api/v1/reviews/" + reviewId + "/comments";
    }

    public static String payload(final ObjectMapper objectMapper, final String body,
        final @Nullable Long parentId) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("body", body);
        if (parentId != null) {
            node.put("parentCommentId", parentId);
        }
        return objectMapper.writeValueAsString(node);
    }

    public static MockHttpServletRequestBuilder request(final ObjectMapper objectMapper, final String token,
        final String url, final String body, final @Nullable Long parentId) {
        return MockMvcRequestBuilders.post(url)
            .header(Accounts.AUTH_HEADER, Accounts.BEARER_PREFIX + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload(objectMapper, body, parentId));
    }
}
