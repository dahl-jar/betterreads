package com.betterreads.testsupport;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.awaitility.Awaitility.await;

public final class UserLockRace {

    private static final String LOCK_USER_SQL = "SELECT user_id FROM app_user WHERE user_id = ? FOR UPDATE";

    private static final String LOCK_WAITERS_SQL = """
        SELECT count(*) FROM pg_stat_activity
        WHERE datname = current_database() AND wait_event_type = 'Lock'
        """;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final TransactionTemplate lockHolder;

    private final JdbcTemplate jdbc;

    public UserLockRace(final JdbcTemplate jdbc) {
        final DataSource dataSource = Objects.requireNonNull(jdbc.getDataSource());
        this.lockHolder = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // PMD.DoNotUseThreads: the race needs real threads
    @SuppressWarnings("PMD.DoNotUseThreads")
    public <S, T> T race(final long userId, final Supplier<S> beforeRacer, final Callable<T> racer,
        final Consumer<S> whileRacerWaits) throws InterruptedException, ExecutionException, TimeoutException {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            final Future<T> raced = lockHolder.execute(status -> {
                jdbc.queryForList(LOCK_USER_SQL, userId);
                final S before = beforeRacer.get();
                final Future<T> pending = pool.submit(racer);
                await().atMost(TIMEOUT).until(() -> pending.isDone() || lockWaiters() > 0);
                whileRacerWaits.accept(before);
                return pending;
            });
            return Objects.requireNonNull(raced).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    private int lockWaiters() {
        return Objects.requireNonNull(jdbc.queryForObject(LOCK_WAITERS_SQL, Integer.class));
    }
}
