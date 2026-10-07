package com.betterreads.features.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.testsupport.Books;
import com.betterreads.testsupport.ContainerizedTest;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogFallbackIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final int CAP = 20;

    private static final String MESSIAH = "Dune Messiah";

    private static final String MESSIAH_ISBN = "9780593098233";

    private static final String DUNE_ID = "fb-1";

    private static final String MESSIAH_ID = "fb-2";

    @Autowired
    private CatalogFallback fallback;

    @Autowired
    private BookRepository books;

    @DynamicPropertySource
    static void meilisearchProps(final DynamicPropertyRegistry registry) {
        MeilisearchServer.register(registry, "books-fallback-test");
    }

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
    }

    @Test
    void shouldFindABookByIsbn13() {
        final long dune = books.save(Books.dune(DUNE_ID)).getBookId();
        save(MESSIAH_ID, MESSIAH, MESSIAH_ISBN);

        final List<Long> ids = fallback.find(Books.DUNE_ISBN);

        assertThat(ids).containsExactly(dune);
    }

    @Test
    void shouldFindABookByHyphenatedIsbn10() {
        final long dune = books.save(Books.dune(DUNE_ID)).getBookId();

        final List<Long> ids = fallback.find("0-441-01359-7");

        assertThat(ids).containsExactly(dune);
    }

    @Test
    void shouldFindABookByIsbn10WithALowercaseX() {
        final long book = save(DUNE_ID, "The Pillars of the Earth", "9780804429573");

        final List<Long> ids = fallback.find("080442957x");

        assertThat(ids).containsExactly(book);
    }

    @Test
    void shouldFindABookByExactTitleInAnyCase() {
        final long dune = books.save(Books.dune(DUNE_ID)).getBookId();
        save(MESSIAH_ID, MESSIAH, MESSIAH_ISBN);

        final List<Long> ids = fallback.find("dUNE");

        assertThat(ids).containsExactly(dune);
    }

    @Test
    void shouldIgnoreSpacesAroundATitle() {
        final long dune = books.save(Books.dune(DUNE_ID)).getBookId();

        final List<Long> ids = fallback.find(" Dune ");

        assertThat(ids).containsExactly(dune);
    }

    @Test
    void shouldNotMatchAPartialTitle() {
        save(MESSIAH_ID, MESSIAH, MESSIAH_ISBN);

        final List<Long> ids = fallback.find(Books.DUNE_TITLE);

        assertThat(ids).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {Books.DUNE_TITLE, Books.DUNE_ISBN})
    void shouldCapAtTwentyBooks(final String query) {
        final List<Long> saved = IntStream.rangeClosed(0, CAP)
            .mapToObj(index -> books.save(Books.dune("fb-" + index)).getBookId())
            .toList();

        final List<Long> ids = fallback.find(query);

        assertThat(ids).containsExactlyElementsOf(saved.subList(0, CAP));
    }

    private long save(final String key, final String title, final String isbn) {
        final Book book = Books.book(key, title);
        book.setIsbn(isbn);
        return books.save(book).getBookId();
    }
}
