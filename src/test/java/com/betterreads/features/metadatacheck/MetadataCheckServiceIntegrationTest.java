package com.betterreads.features.metadatacheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.betterreads.book.AuthorRepository;
import com.betterreads.book.BookRepository;
import com.betterreads.book.BookUpsertService;
import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.websearch.MetadataCheckClient;
import com.betterreads.clients.websearch.MetadataCheckRequest;
import com.betterreads.clients.websearch.SourceGroup;
import com.betterreads.testsupport.ContainerizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class MetadataCheckServiceIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final SeriesEntry SERIES = new SeriesEntry("series", 1);

    private static final SeriesEntry UNIVERSE = new SeriesEntry("universe", 1);

    @MockitoBean
    private MetadataCheckClient client;

    @Autowired
    private MetadataCheckService service;

    @Autowired
    private BookUpsertService upsert;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @BeforeEach
    void setUp() {
        books.deleteAll();
        authors.deleteAll();
        when(client.check(any(), any())).thenReturn(MetadataCheckSamples.outcome(Map.of()));
    }

    @Test
    void shouldSendCollectionsOfDetachedBook() {
        final SourceBook.Builder book = MetadataCheckSamples.source();
        book.series(List.of(SERIES, UNIVERSE));
        book.rawSubjects(List.of("Fantasy"));
        upsert.upsertFromSource(book.build());
        final ArgumentCaptor<List<MetadataCheckRequest>> batch = ArgumentCaptor.captor();
        final ArgumentCaptor<SourceGroup> group = ArgumentCaptor.captor();

        service.checkDueBooks();

        verify(client).check(batch.capture(), group.capture());
        final List<MetadataCheckRequest> requests = batch.getValue();
        final MetadataCheckRequest request = requests.getFirst();
        assertThat(request.authors()).containsExactly(MetadataCheckSamples.AUTHOR);
        assertThat(request.universe()).isEqualTo(UNIVERSE);
        assertThat(group.getValue()).isEqualTo(SourceGroup.SFF);
    }
}
