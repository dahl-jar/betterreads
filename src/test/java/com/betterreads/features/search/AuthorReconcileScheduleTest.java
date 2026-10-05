package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "betterreads.catalog.staging.poll-enabled=false",
    "betterreads.search.author-reconcile-cron=-"
})
class AuthorReconcileScheduleTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private List<ScheduledTaskHolder> holders;

    @DynamicPropertySource
    static void meilisearch(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, "books-author-reconcile-schedule-test");
    }

    @Test
    void shouldNotScheduleReconcileWhenCronIsDisabled() {
        final List<String> tasks = holders.stream()
            .flatMap(holder -> holder.getScheduledTasks().stream())
            .map(ScheduledTask::toString)
            .toList();

        assertThat(tasks).anyMatch(task -> task.contains("CreditBackfillScheduler.scheduledBackfill"))
            .noneMatch(task -> task.contains("AuthorIndexer.reconcile"));
    }
}
