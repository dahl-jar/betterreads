package com.betterreads.auth.deletion;

import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard-deletes users whose soft-delete is older than the configured grace period. Tests call
 * {@link #sweep()} directly so timing stays deterministic; production runs through
 * {@link AccountDeletionScheduler}.
 *
 * <p>Uses a native SQL {@code DELETE FROM app_user} so the {@code @SQLRestriction} on
 * {@link com.betterreads.auth.entity.User} (which hides deleted rows from JPA queries) does not
 * apply. Dependent rows are removed by the foreign-key cascades configured in the migrations:
 * {@code refresh_token}, {@code email_token}, {@code review}, {@code collection},
 * {@code user_book_interaction}, {@code user_book_signal}, and {@code user_recommendation} all
 * cascade; {@code activity_event.actor_user_id} is set to {@code NULL} so the audit trail
 * survives the user's deletion.
 */
@Component
public class AccountDeletionSweep {

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionSweep.class);

    private final EntityManager entityManager;

    private final AccountDeletionProperties properties;

    public AccountDeletionSweep(
        final EntityManager entityManager,
        final AccountDeletionProperties properties
    ) {
        this.entityManager = entityManager;
        this.properties = properties;
    }

    /**
     * Hard-deletes every {@code app_user} row whose {@code deleted_at} is older than the
     * configured grace period. Returns the number of rows removed.
     */
    @Transactional
    public int sweep() {
        final Instant cutoff = Instant.now().minus(Duration.ofHours(properties.gracePeriodHours()));
        final int removed = entityManager.createNativeQuery(
                "DELETE FROM app_user WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
            .setParameter("cutoff", Timestamp.from(cutoff))
            .executeUpdate();
        if (removed > 0) {
            LOG.info("auth.account-deletion.sweep hard-deleted past-grace users count={} cutoff={}",
                removed, cutoff);
        }
        return removed;
    }
}
