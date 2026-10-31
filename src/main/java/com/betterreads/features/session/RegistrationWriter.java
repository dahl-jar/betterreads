package com.betterreads.features.session;

import com.betterreads.errors.BusinessRuleException;
import com.betterreads.logging.LogSanitizer;
import com.betterreads.mailoutbox.MailOutboxService;
import com.betterreads.users.EmailVerificationIssuer;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RegistrationWriter {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationWriter.class);

    private static final String DUPLICATE_USERNAME = "Username already taken";

    private static final Duration SIGNUP_NOTICE_WINDOW = Duration.ofHours(24);

    private final UserRepository userRepository;

    private final EmailVerificationIssuer emailVerificationIssuer;

    private final MailOutboxService mailOutbox;

    RegistrationWriter(
        final UserRepository userRepository,
        final EmailVerificationIssuer emailVerificationIssuer,
        final MailOutboxService mailOutbox
    ) {
        this.userRepository = userRepository;
        this.emailVerificationIssuer = emailVerificationIssuer;
        this.mailOutbox = mailOutbox;
    }

    @Transactional(readOnly = true)
    public void rejectHeldUsername(final String username) {
        if (userRepository.isUsernameHeldByAnyRow(username)) {
            throw new BusinessRuleException(DUPLICATE_USERNAME);
        }
    }

    @Transactional
    public void create(final User user) {
        final User saved = userRepository.saveAndFlush(user);
        emailVerificationIssuer.issueVerification(saved.getUserId(), saved.getEmail());
        LOG.info("Registered new user userId={} username={}",
            saved.getUserId(), LogSanitizer.forLog(saved.getUsername()));
    }

    @Transactional
    public void answerTakenEmail(final String email) {
        userRepository.findByEmailForUpdate(email).ifPresentOrElse(
            this::notifyOwner,
            () -> LOG.info("Registration on an email in deletion grace, no mail queued"));
    }

    @Transactional
    public boolean resolveClash(final String username, final String email) {
        rejectHeldUsername(username);
        if (!userRepository.isEmailHeldByAnyRow(email)) {
            return false;
        }
        answerTakenEmail(email);
        return true;
    }

    private void notifyOwner(final User owner) {
        final Instant now = Instant.now();
        final Instant lastSent = owner.getSignupNoticeSentAt();
        if (lastSent != null && lastSent.isAfter(now.minus(SIGNUP_NOTICE_WINDOW))) {
            LOG.info("Signup notice skipped, one was queued inside the window userId={}", owner.getUserId());
            return;
        }
        owner.setSignupNoticeSentAt(now);
        userRepository.save(owner);
        mailOutbox.enqueueSignupAttempt(owner.getEmail());
        LOG.info("Queued signup notice userId={}", owner.getUserId());
    }
}
