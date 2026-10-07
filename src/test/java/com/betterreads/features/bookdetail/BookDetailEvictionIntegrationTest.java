package com.betterreads.features.bookdetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.betterreads.book.AuthorRepository;
import com.betterreads.book.BookDetailCache;
import com.betterreads.book.BookRepository;
import com.betterreads.book.BookUpsertService;
import com.betterreads.book.VerifiedMetadata;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.testsupport.ContainerizedTest;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Cached details refresh on every write, and entries written before a detail field existed still read. */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookDetailEvictionIntegrationTest extends ContainerizedTest {

    private static final String ISBN = "9780553103540";

    private static final String ORIGINAL_TITLE = "A Game of Thrones";

    private static final String AUTHOR = "George R. R. Martin";

    private static final String OTHER_AUTHOR = "Other Author";

    private static final int PUBLISH_YEAR = 1996;

    private static final String CACHED_WITHOUT_SERIES = "{\"key\": \"%s\", \"complete\": true, \"title\": \"%s\", "
        + "\"authors\": [], \"subjects\": [], \"awards\": []}";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    @Autowired
    private BookUpsertService bookUpsertService;

    @Autowired
    private BookDetailService bookDetailService;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearCatalog() {
        books.deleteAll();
        authors.deleteAll();
        cacheManager.getCache(BookDetailCache.NAME).clear();
    }

    static Stream<Arguments> writes() {
        final BiConsumer<BookUpsertService, Long> upsert = (service, bookId) ->
            service.upsertFromSource(book(OTHER_AUTHOR));
        final BiConsumer<BookUpsertService, Long> credits = (service, bookId) ->
            service.applyCredits(bookId, List.of(SourceAuthor.ofName(OTHER_AUTHOR)));
        final BiConsumer<BookUpsertService, Long> verified = (service, bookId) -> service.applyVerified(bookId,
            new VerifiedMetadata(null, List.of(OTHER_AUTHOR), null, null, null, null, null, null), 1);
        return Stream.of(
            arguments("upsertFromSource", upsert),
            arguments("applyCredits", credits),
            arguments("applyVerified", verified));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writes")
    void shouldEvictTheDetailCacheOnEveryWrite(final String name, final BiConsumer<BookUpsertService, Long> write) {
        final long bookId = bookUpsertService.upsertFromSource(book(AUTHOR)).getBookId();
        bookDetailService.findByKey(ISBN);

        write.accept(bookUpsertService, bookId);

        final BookDetailResponse fresh = bookDetailService.findByKey(ISBN).orElseThrow();
        assertThat(fresh.authors()).as("detail after %s", name).containsExactly(OTHER_AUTHOR);
    }

    @Test
    void shouldReadAnEntryCachedWithoutSeries() {
        final String cached = CACHED_WITHOUT_SERIES.formatted(ISBN, ORIGINAL_TITLE);
        redis.opsForValue().set(BookDetailCache.NAME + "::" + ISBN, cached);

        final Optional<BookDetailResponse> detail = bookDetailService.findByKey(ISBN);

        assertThat(detail).get().satisfies(response -> assertThat(response.series()).isEmpty());
    }

    private static SourceBook book(final String author) {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(ISBN)
            .openLibraryWorkKey("OL_AGOT")
            .title(ORIGINAL_TITLE)
            .description("Noble families vie for the Iron Throne in Westeros.")
            .coverUrl("https://covers.example/agot.jpg")
            .publicationYear(PUBLISH_YEAR)
            .authors(List.of(SourceAuthor.ofName(author)))
            .build();
    }
}
