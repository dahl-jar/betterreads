package com.betterreads.features.account;

import com.betterreads.mailoutbox.MailOutbox;
import com.betterreads.mailoutbox.MailOutboxRepository;
import com.betterreads.testsupport.Accounts;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class AccountTestFixture {

    static final String FORGOT_URL = "/api/v1/auth/forgot-password";

    static final String UNKNOWN_EMAIL = "ghost@example.com";

    static final String UNKNOWN_TOKEN = "not-a-real-token";

    static final String FIELD_TOKEN = "token";

    private static final int CONCURRENT_THREADS = 16;

    private static final int CONCURRENT_TIMEOUT_SECONDS = 10;

    private AccountTestFixture() {
    }

    static String emailPayload(final ObjectMapper objectMapper, final String email) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("email", email);
        return objectMapper.writeValueAsString(node);
    }

    static String readEnqueuedToken(final MailOutboxRepository outbox, final ObjectMapper objectMapper) {
        final MailOutbox row = outbox.findAll().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("expected an enqueued mail row but found none"));
        return payloadField(objectMapper, row, FIELD_TOKEN);
    }

    static String payloadField(final ObjectMapper objectMapper, final MailOutbox row, final String field) {
        try {
            return objectMapper.readTree(row.getPayload()).path(field).asString();
        } catch (final JacksonException ex) {
            throw new IllegalStateException("malformed outbox payload", ex);
        }
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    static long registerUser(
        final MockMvc mockMvc,
        final ObjectMapper objectMapper,
        final UserRepository users,
        final String username,
        final String email
    ) throws Exception {
        mockMvc.perform(post(Accounts.REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Accounts.registerPayload(objectMapper, username, email, Accounts.PASSWORD)))
            .andExpect(status().isCreated());
        final User user = users.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("registration succeeded but user not found"));
        return user.getUserId();
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    static MvcResult login(
        final MockMvc mockMvc,
        final ObjectMapper objectMapper,
        final String identifier,
        final String password
    ) throws Exception {
        return mockMvc.perform(post(Accounts.LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Accounts.loginPayload(objectMapper, identifier, password)))
            .andExpect(status().isOk())
            .andReturn();
    }

    static void expireTokens(
        final JdbcTemplate jdbcTemplate,
        final long userId,
        final EmailToken.Purpose purpose
    ) {
        final OffsetDateTime issued = Accounts.expiredIssuedAt().atOffset(ZoneOffset.UTC);
        jdbcTemplate.update(
            "UPDATE email_token SET issued_at = ?, expires_at = ? "
                + "WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL",
            issued, issued.plusSeconds(1), userId, purpose.name());
    }

    // PMD.DoNotUseThreads: the race needs real threads
    @SuppressWarnings("PMD.DoNotUseThreads")
    static void runConcurrently(final Runnable action)
        throws InterruptedException, ExecutionException, TimeoutException {
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> futures = new ArrayList<>(CONCURRENT_THREADS);

        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS)) {
            for (int i = 0; i < CONCURRENT_THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    action.run();
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> future : futures) {
                future.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }
}
