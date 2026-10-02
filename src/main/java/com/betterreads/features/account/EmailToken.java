package com.betterreads.features.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import com.betterreads.db.HashedToken;
import org.jspecify.annotations.Nullable;

/**
 * One row per single-use token sent over email.
 *
 * <p>Only the hash is stored. The plaintext lives only in the email link. A partial unique
 * index limits each user to one active token per purpose.
 */
@Entity
@Table(name = "email_token")
// NullAway.Init: JPA sets the fields reflectively.
@SuppressWarnings("NullAway.Init")
public class EmailToken extends HashedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_token_id")
    private Long emailTokenId;

    @Column(name = "purpose", nullable = false, columnDefinition = "TEXT")
    @Enumerated(EnumType.STRING)
    private Purpose purpose;

    @Column(name = "consumed_at")
    @Nullable
    private Instant consumedAt;

    public void setPurpose(final Purpose purpose) {
        this.purpose = purpose;
    }

    @Nullable
    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(@Nullable final Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    /** Which feature a token belongs to. A DB CHECK constraint lists the same names. */
    public enum Purpose {
        PASSWORD_RESET,
        EMAIL_VERIFICATION
    }
}
