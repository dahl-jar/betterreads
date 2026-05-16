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
 * Hard-deletes users whose soft-delete is older than the grace period.
 *
 * <p>Uses native SQL so the {@code @SQLRestriction} on {@link com.betterreads.auth.entity.User}
 * (which hides soft-deleted rows from JPA) does not filter them out. Dependent rows are
 * removed by the FK cascades set up in the migrations.
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

    /** Hard-deletes users past the grace period and returns the number of rows removed. */
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
