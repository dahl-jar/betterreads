package com.betterreads.auth.emailverification;

import com.betterreads.auth.entity.User;
import com.betterreads.auth.repository.UserRepository;
import com.betterreads.auth.token.EmailToken;
import com.betterreads.auth.token.EmailTokenRepository;
import com.betterreads.common.crypto.HmacTokenHasher;
import com.betterreads.common.exception.InvalidRequestException;
import com.betterreads.mail.outbox.MailOutboxService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes single-use email-verification tokens. Plaintext is 256-bit random and
 * never stored; only the HMAC-SHA256 hash sits in the DB.
 *
 * <p>The {@code requestResend} side does not reveal whether the email exists or whether the
 * account is already verified: the controller returns {@code 204} for every branch, and the
 * mailer is only invoked when an unverified account actually matches.
 *
 * <p>Verification is idempotent: presenting the same token twice returns silently the second
 * time so a user who clicks the link from two devices, or whose mail client prefetches the link,
 * does not see an error. This is different from password reset (where replay is rejected),
 * because verification is non-destructive.
 *
 * <p>Lock-order rule: every flow that mutates an {@link com.betterreads.auth.token.EmailToken}
 * for a user must lock the {@code app_user} row first via
 * {@link UserRepository#findByEmailForUpdate(String)} or
 * {@link UserRepository#findByIdForUpdate(long)}. The verify path uses a two-phase lookup (find
 * the token unlocked to discover its user_id, then lock the user, then re-fetch the token) so
 * issue-vs-verify cannot deadlock on opposite lock orders.
 */
@Service
public class EmailVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int TOKEN_BYTES = 32;

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    private static final String INVALID_OR_EXPIRED_TOKEN = "Invalid or expired verification token";

    private final UserRepository userRepository;

    private final EmailTokenRepository tokenRepository;

    private final HmacTokenHasher hasher;

    private final MailOutboxService mailOutbox;

    private final SecureRandom random;

    public EmailVerificationService(
        final UserRepository userRepository,
        final EmailTokenRepository tokenRepository,
        final HmacTokenHasher hasher,
        final MailOutboxService mailOutbox
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.hasher = hasher;
        this.mailOutbox = mailOutbox;
        this.random = new SecureRandom();
    }

    /**
     * Issues a fresh verification token for the given user and enqueues the verification email.
     * Consumes any prior outstanding tokens for the user so only the latest link works.
     *
     * <p>Caller contract: the user row must be either brand-new in this transaction (insert
     * during registration, no other tx can see it under MVCC isolation) or held under a
     * {@code SELECT ... FOR UPDATE} lock. Today the two callers are
     * {@link com.betterreads.auth.service.AuthServiceImpl#register} (brand-new row) and
     * {@link #requestResend(String)} (explicit lock). Without serialization on the user row, a
     * concurrent issue could insert a second active token before this one's enqueue commits.
     *
     * <p>Joins the caller's transaction via the default {@code Propagation.REQUIRED} so the
     * register path commits the user insert, the token, and the outbox row atomically.
     */
    @Transactional
    public void issueVerification(final long userId, final String recipient) {
        consumeOutstandingTokens(userId);
        final String plaintext = generatePlaintext();
        insertNewToken(userId, plaintext);
        mailOutbox.enqueueEmailVerification(recipient, plaintext);
        LOG.info("Issued verification token userId={}", userId);
    }

    /**
     * Resends a verification link for the account matching {@code email}. Returns silently for
     * unknown addresses and for accounts that are already verified so the response cannot be
     * used to enumerate registered or unverified emails.
     *
     * <p>Locks the user row before the consume-insert-enqueue sequence so two concurrent
     * resends for the same user serialize. {@code issueVerification} runs as part of the same
     * transaction via {@code Propagation.REQUIRED}.
     */
    @Transactional
    public void requestResend(final String email) {
        final String normalized = normalizeEmail(email);
        final Optional<User> userOpt = userRepository.findByEmailForUpdate(normalized);
        if (userOpt.isEmpty()) {
            LOG.info("Resend request for unknown email, no token issued");
            return;
        }
        final User user = userOpt.get();
        if (user.getEmailVerifiedAt() != null) {
            LOG.info("Resend skipped: account already verified userId={}", user.getUserId());
            return;
        }
        issueVerification(user.getUserId(), user.getEmail());
    }

    /**
     * Marks every active token for the user as consumed. Flushes at the end to force the
     * UPDATEs to land before the next INSERT in this transaction; Hibernate's default action
     * queue executes inserts before updates within the same flush, which would hit the partial
     * unique index on the still-active prior row even though this consume marks it inactive.
     */
    private void consumeOutstandingTokens(final long userId) {
        final Instant now = Instant.now();
        tokenRepository.findActive(userId, EmailToken.Purpose.EMAIL_VERIFICATION).forEach(t -> {
            t.setConsumedAt(now);
            tokenRepository.save(t);
        });
        tokenRepository.flush();
    }

    private void insertNewToken(final long userId, final String plaintext) {
        final EmailToken row = new EmailToken();
        row.setUserId(userId);
        row.setPurpose(EmailToken.Purpose.EMAIL_VERIFICATION);
        row.setTokenHash(hasher.hash(plaintext));
        row.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        tokenRepository.saveAndFlush(row);
    }

    /**
     * Consumes the presented verification token and flips {@code email_verified_at} on the
     * matching user. Throws {@link InvalidRequestException} when the token is unknown, expired,
     * superseded by a later resend, or refers to a deleted user.
     *
     * <p>Idempotent replay returns silently only when the token is already consumed AND the
     * user is already verified (the same token clicked twice). A consumed token whose user is
     * still unverified means the token was superseded by a later resend, so this path returns
     * the same {@code invalid/expired} error as an unknown token; only the freshly issued
     * token can complete verification.
     *
     * <p>Two-phase lookup to honor the project-wide user-first lock order: find the token
     * unlocked to discover its user_id, then lock the user via
     * {@link UserRepository#findByIdForUpdate(long)}, then re-fetch the token under that lock
     * so the row state is fresh. Without the user lock, two concurrent verifies on the same
     * token could both pass the {@code consumed_at IS NULL} check.
     */
    @Transactional
    public void verify(final String presentedToken) {
        final String hash = hasher.hash(presentedToken);
        final EmailToken initial = tokenRepository
            .findByHashAndPurpose(hash, EmailToken.Purpose.EMAIL_VERIFICATION)
            .orElseThrow(() -> {
                LOG.warn("Verification rejected: token unknown");
                return new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
            });
        final User user = userRepository.findByIdForUpdate(initial.getUserId())
            .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN));
        final EmailToken row = tokenRepository
            .findByHashAndPurpose(hash, EmailToken.Purpose.EMAIL_VERIFICATION)
            .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN));

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
        row.setConsumedAt(now);
        tokenRepository.save(row);
        LOG.info("Verified email userId={}", user.getUserId());
    }

    /**
     * A consumed token is legal only when the user is already verified (the same link clicked
     * twice). A consumed token whose user is still unverified means the token was superseded by
     * a later resend; the freshly issued token is the one that completes verification.
     */
    private void assertReplay(final EmailToken row, final User user) {
        if (user.getEmailVerifiedAt() != null) {
            LOG.info("Verification replay accepted, user already verified userId={}", row.getUserId());
            return;
        }
        LOG.warn("Verification rejected: token superseded by later resend userId={}", row.getUserId());
        throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN);
    }

    private static String normalizeEmail(final String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generatePlaintext() {
        final byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
