package com.betterreads.testsupport;

import com.betterreads.book.BookRepository;
import com.betterreads.ratelimit.RateLimitFilter;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.ObjectMapper;

import static com.betterreads.testsupport.Accounts.LOGIN_URL;
import static com.betterreads.testsupport.Accounts.PASSWORD;
import static com.betterreads.testsupport.Accounts.REGISTER_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
    "auth.rate-limit.register-capacity=1000",
    "auth.rate-limit.register-refill-tokens=1000",
    "auth.rate-limit.register-refill-seconds=1",
    "auth.rate-limit.login-capacity=1000",
    "auth.rate-limit.login-refill-tokens=1000",
    "auth.rate-limit.login-refill-seconds=1",
    "mail.app-base-url=https://test.example.com",
    "mail.outbox.worker-enabled=false"
})
public abstract class RegisteredUserTest extends ContainerizedTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RateLimitFilter rateLimit;

    @Autowired
    private BookRepository books;

    protected void resetToDune() {
        jdbc.update("DELETE FROM review");
        jdbc.update("DELETE FROM book_author");
        jdbc.update("DELETE FROM book");
        jdbc.update("DELETE FROM app_user");
        rateLimit.reset();
        Books.seedBook(books, Books.DUNE_KEY, Books.DUNE_TITLE);
    }

    // PMD.SignatureDeclareThrowsException: MockMvc.perform declares throws Exception.
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    protected String registerAndLogin(final String username, final String email) throws Exception {
        final MockMvc mockMvc = securedMockMvc();
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Accounts.registerPayload(objectMapper, username, email, PASSWORD)))
            .andExpect(status().isCreated());
        final MvcResult login = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(Accounts.loginPayload(objectMapper, username, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        final String body = login.getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/accessToken").asString();
    }

    protected long idOf(final ResultActions created) throws UnsupportedEncodingException {
        final String body = created.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asLong();
    }
}
