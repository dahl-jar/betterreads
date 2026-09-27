package com.betterreads.features.account;

import com.betterreads.errors.InvalidRequestException;
import com.betterreads.mailoutbox.MailOutboxService;
import com.betterreads.users.EmailNormalizer;
import com.betterreads.users.EmailVerificationIssuer;
import com.betterreads.users.User;
import com.betterreads.users.UserRepository;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use email-verification tokens.
 *
 * <p>Every path locks the user row before the token row, so issue and verify cannot deadlock.
 */
@Service
class EmailVerificationService implements EmailVerificationIssuer {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired verification token";

    private final UserRepository userRepository;

    private final EmailTokenIssuer tokenIssuer;

    private final EmailTokenRedeemer redeemer;

    private final MailOutboxService mailOutbox;

    EmailVerificationService(
        final UserRepository userRepository,
        final EmailTokenIssuer tokenIssuer,
        final EmailTokenRedeemer redeemer,
        final MailOutboxService mailOutbox
    ) {
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.redeemer = redeemer;
        this.mailOutbox = mailOutbox;
    }

    /** Older tokens for the user are consumed, so only the latest link works. */
    @Transactional
    @Override
    public void issueVerification(final long userId, final String recipient) {
        final String plaintext =
            tokenIssuer.issue(userId, EmailToken.Purpose.EMAIL_VERIFICATION, TOKEN_LIFETIME);
        mailOutbox.enqueueEmailVerification(recipient, plaintext);
        LOG.info("Issued verification token userId={}", userId);
    }

    /**
     * Silent no-op for unknown addresses and already-verified accounts, so the response cannot
     * be used to enumerate registered or unverified emails.
     */
    @Transactional
    public void requestResend(final String email) {
        final String normalized = EmailNormalizer.normalize(email);
        userRepository.findByEmailForUpdate(normalized).ifPresentOrElse(
            this::resendTo,
            () -> LOG.info("Resend request for unknown email, no token issued"));
    }

    private void resendTo(final User user) {
        if (user.getEmailVerifiedAt() != null) {
            LOG.info("Resend skipped: account already verified userId={}", user.getUserId());
            return;
        }
        issueVerification(user.getUserId(), user.getEmail());
    }

    /**
     * Throws {@code InvalidRequestException} when the token is unknown, expired, superseded, or
     * belongs to a deleted user.
     *
     * <p>A replay is only accepted when the user is already verified (same link clicked
     * twice). A consumed token whose user is still unverified means a later resend
     * superseded it, so it is rejected like an unknown token.
     */
    @Transactional
    public void verify(final String presentedToken) {
        final EmailTokenRedeemer.Locked locked = redeemer.lockForRedeem(
            presentedToken, EmailToken.Purpose.EMAIL_VERIFICATION, INVALID_OR_EXPIRED_TOKEN);
        final User user = locked.user();
        final EmailToken row = locked.token();

        if (row.getConsumedAt() != null) {
            assertReplay(row, user);
            return;
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            LOG.warn("Verification rejected: token expired userId={}", row.getUserId());
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
        }

        final Instant now = Instant.now();
        user.setEmailVerifiedAt(now);
        userRepository.save(user);
        redeemer.consume(row, now);
        LOG.info("Verified email userId={}", user.getUserId());
    }

    private void assertReplay(final EmailToken row, final User user) {
        if (user.getEmailVerifiedAt() != null) {
            LOG.info("Verification replay accepted, user already verified userId={}", row.getUserId());
            return;
        }
        LOG.warn("Verification rejected: token superseded by later resend userId={}", row.getUserId());
        throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
    }
}
