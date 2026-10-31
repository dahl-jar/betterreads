package com.betterreads.features.session;

import com.betterreads.testsupport.Accounts;
import com.betterreads.testsupport.ContainerizedTest;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "mail.outbox.worker-enabled=false")
class RegisterConstraintErrorIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String EMAIL_CONSTRAINT = "app_user_email_key";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldKeepEmailOutOfConstraintError() {
        Accounts.seedUser(userRepository, passwordEncoder, Accounts.USER, Accounts.USER_EMAIL, Accounts.PASSWORD);
        final User duplicate = new User();
        duplicate.setUsername(Accounts.OTHER_USER);
        duplicate.setEmail(Accounts.USER_EMAIL);
        duplicate.setPasswordHash(Objects.requireNonNull(passwordEncoder.encode(Accounts.PASSWORD)));

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining(EMAIL_CONSTRAINT)
            .hasMessageNotContaining(Accounts.USER_EMAIL);
    }
}
