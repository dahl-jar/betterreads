package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.book.Author;
import com.betterreads.book.AuthorRepository;
import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Results;
import java.util.List;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "betterreads.search.reconcile-page-size=1")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class BookIndexReconcilerIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String INDEX_NAME = "books-reconcile-test";

    private static final String BACKDATE =
        "UPDATE book SET updated_at = now() - interval '3 days' WHERE book_id = ?";

    private static final String RETITLE = "UPDATE book SET title = ?, updated_at = now() WHERE book_id = ?";

    private static final String ORPHAN = "rc-orphan";

    @Autowired
    private BookIndexReconciler reconciler;

    @Autowired
    private BookSearchService searchService;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Client client;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, INDEX_NAME);
    }

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
        authors.deleteAll();
        final Index index = client.index(INDEX_NAME);
        index.waitForTask(index.deleteAllDocuments().getTaskUid());
    }

    @Test
    void shouldRefreshStaleDocumentsOfRecentlyChangedBooks() {
        final String dune = "rc-1";
        final String hyperion = "rc-2";
        final long first = saveBook(dune, "Dune", "Frank Herbert");
        final long second = saveBook(hyperion, "Hyperion", "Dan Simmons");
        reconciler.reconcile();
        jdbc.update(RETITLE, "Dune Messiah", first);
        jdbc.update(RETITLE, "The Fall of Hyperion", second);

        reconciler.reconcile();

        assertThat(searchService.hitFor("messiah", dune)).isPresent();
        assertThat(searchService.hitFor("fall", hyperion)).isPresent();
    }

    @Test
    void shouldIndexBooksMissingFromTheIndex(final CapturedOutput output) {
        final String kindred = "rc-6";
        final String ubik = "rc-7";
        final long solaris = saveBook("rc-5", "Solaris", "Stanislaw Lem");
        final long first = saveBook(kindred, "Kindred", "Octavia E. Butler");
        final long second = saveBook(ubik, "Ubik", "Philip K. Dick");
        reconciler.reconcile();
        List.of(solaris, first, second).forEach(bookId -> jdbc.update(BACKDATE, bookId));
        MeilisearchServer.removeFromIndex(client, INDEX_NAME, kindred);
        MeilisearchServer.removeFromIndex(client, INDEX_NAME, ubik);

        reconciler.reconcile();

        assertThat(searchService.hitFor("kindred", kindred)).isPresent();
        assertThat(searchService.hitFor("ubik", ubik)).isPresent();
        assertThat(output).contains("search.reconcile-full missing=2 orphaned=0");
    }

    @Test
    void shouldDeleteDocumentsWithNoBook(final CapturedOutput output) {
        indexDoc(ORPHAN, ORPHAN);

        reconciler.reconcile();

        final Index index = client.index(INDEX_NAME);
        final Results<BookSearchDocument> documents = index.getDocuments(BookSearchDocument.class);
        assertThat(documents.getTotal()).isZero();
        assertThat(output).contains("search.reconcile-full missing=0 orphaned=1");
    }

    @Test
    void shouldFindMissingBooksAcrossIndexPages(final CapturedOutput output) {
        indexDoc(ORPHAN, ORPHAN);
        synced("rc-9", "Blindsight", "Peter Watts");
        synced("rc-10", "Annihilation", "Jeff VanderMeer");
        final String piranesi = "rc-11";
        jdbc.update(BACKDATE, saveBook(piranesi, "Piranesi", "Susanna Clarke"));

        reconciler.reconcile();

        assertThat(searchService.hitFor("piranesi", piranesi)).isPresent();
        assertThat(output).contains("search.reconcile-full missing=1 orphaned=1");
    }

    private void synced(final String key, final String title, final String authorName) {
        indexDoc(key, title);
        jdbc.update(BACKDATE, saveBook(key, title, authorName));
    }

    private void indexDoc(final String id, final String title) {
        searchService.index(List.of(BookSearchDocument.builder(id).title(title).build()));
    }

    private long saveBook(final String key, final String title, final String authorName) {
        final Author author = authors.save(Books.author(authorName));
        final Book book = Books.promoted(key, title, author);
        book.setHardcoverId(key);
        return books.save(book).getBookId();
    }
}
