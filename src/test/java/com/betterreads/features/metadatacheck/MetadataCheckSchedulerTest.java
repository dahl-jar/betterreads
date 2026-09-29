package com.betterreads.features.metadatacheck;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

class MetadataCheckSchedulerTest {

    private final MetadataCheckService service = mock(MetadataCheckService.class);

    private final Executor sameThread = Runnable::run;

    private MetadataCheckScheduler scheduler(final boolean enabled) {
        return new MetadataCheckScheduler(service, MetadataCheckSamples.properties(enabled), sameThread);
    }

    @Test
    void shouldRunWhenEnabled() {
        scheduler(true).scheduledCheck();

        verify(service).checkNewBooks();
    }

    @Test
    void shouldSkipWhenDisabled() {
        scheduler(false).scheduledCheck();

        verify(service, never()).checkNewBooks();
    }
}
