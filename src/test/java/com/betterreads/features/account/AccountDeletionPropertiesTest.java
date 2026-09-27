package com.betterreads.features.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountDeletionPropertiesTest {

    private static final long THIRTY_DAYS_IN_HOURS = 720L;

    private static final long SIX_HOURS = 6L;

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void shouldUseThirtyDaysWhenGracePeriodIsNotPositive(final long configured) {
        final AccountDeletionProperties properties = new AccountDeletionProperties(configured, true);

        assertThat(properties.gracePeriodHours()).isEqualTo(THIRTY_DAYS_IN_HOURS);
    }

    @Test
    void shouldKeepPositiveGracePeriod() {
        final AccountDeletionProperties properties = new AccountDeletionProperties(SIX_HOURS, true);

        assertThat(properties.gracePeriodHours()).isEqualTo(SIX_HOURS);
    }
}
