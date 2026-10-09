package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.APPLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.GOOGLE_COVER;
import static com.betterreads.features.coverimages.CoverImageFixtures.ISBN;
import static com.betterreads.features.coverimages.CoverImageFixtures.STORE;
import static com.betterreads.features.coverimages.CoverImageFixtures.bytes;
import static com.betterreads.features.coverimages.CoverImageFixtures.image;
import static com.betterreads.features.coverimages.CoverImageFixtures.wayOfKings;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.betterreads.book.Book;
import com.betterreads.clients.hardcoverbook.HardcoverClient;
import com.betterreads.clients.itunes.ItunesApi;
import com.betterreads.clients.itunes.ItunesBook;
import com.betterreads.clients.openlibrary.OpenLibraryClient;
import com.betterreads.images.CoverFetcher;
import com.betterreads.testsupport.ContainerizedTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

abstract class CoverUpgradeJobFixture extends ContainerizedTest {

    static final String OTHER_ISBN = "9780765326362";

    static final String GOOGLE = "GOOGLE_BOOKS";

    static final String APPLE = "APPLE_BOOKS";

    static final String HARDCOVER = "HARDCOVER";

    static final String OBJECT_KEY = "covers/" + ISBN + "/1";

    static final String SEARCHED = "true";

    static final String UNSEARCHED = "false";

    static final String UNCHANGED_GOOGLE = GOOGLE_COVER + " " + GOOGLE + " none none ";

    static final String APPLIED_APPLE = applied(APPLE_COVER);

    static final String CLEARED = "none none none none " + SEARCHED;

    static final String BLOCK_SQL = "INSERT INTO cover_blocked (url) VALUES (?)";

    static final String OLD_SEARCH_SQL =
        "UPDATE book SET cover_searched_at = now() - interval '60 days' WHERE book_id = ?";

    private static final String COVER_SQL = "SELECT coalesce(cover_url, 'none')"
        + " || ' ' || coalesce(cover_source, 'none')"
        + " || ' ' || coalesce(cover_store_url, 'none') || ' ' || coalesce(cover_object_key, 'none')"
        + " || ' ' || (cover_searched_at IS NOT NULL) FROM book WHERE isbn = ?";

    @MockitoBean
    private ItunesApi itunesApi;

    @MockitoBean
    private HardcoverClient hardcover;

    @MockitoBean
    private OpenLibraryClient openLibrary;

    @MockitoBean
    private CoverFetcher fetcher;

    @MockitoBean
    private CoverCheck coverCheck;

    @MockitoBean
    private CoverMirrorService coverMirror;

    @Autowired
    private CoverUpgradeJob upgradeJob;

    @Autowired
    private BookCoverRepository coverRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM cover_blocked");
        coverRepository.deleteAll();
        when(fetcher.fetch(anyString())).thenAnswer(call -> Optional.of(image(call.getArgument(0))));
        when(coverCheck.passes(bytes(APPLE_COVER))).thenReturn(true);
        when(coverMirror.mirror(anyString(), anyString(), any())).thenReturn(Optional.of(OBJECT_KEY));
        when(itunesApi.lookupByIsbn(anyString())).thenReturn(Optional.of(new ItunesBook(APPLE_COVER, STORE)));
    }

    long save(final String isbn, final @Nullable String coverUrl, final @Nullable String source) {
        final Book book = new Book();
        book.applyFrom(wayOfKings(coverUrl).isbn13(isbn).build());
        final Book saved = coverRepository.save(book);
        jdbcTemplate.update("UPDATE book SET cover_source = ? WHERE book_id = ?", source, saved.getBookId());
        return saved.getBookId();
    }

    static String applied(final String url) {
        return url + " " + APPLE + " " + STORE + " " + OBJECT_KEY + " " + SEARCHED;
    }

    long wrongCover() {
        final long bookId = save(ISBN, GOOGLE_COVER, GOOGLE);
        when(itunesApi.lookupByIsbn(ISBN)).thenReturn(Optional.empty());
        when(coverCheck.isWrongImage(bytes(GOOGLE_COVER))).thenReturn(true);
        return bookId;
    }

    String cover(final String isbn) {
        return jdbcTemplate.queryForObject(COVER_SQL, String.class, isbn);
    }

    ItunesApi itunes() {
        return itunesApi;
    }

    CoverCheck check() {
        return coverCheck;
    }

    CoverMirrorService mirror() {
        return coverMirror;
    }

    CoverUpgradeJob job() {
        return upgradeJob;
    }

    BookCoverRepository books() {
        return coverRepository;
    }

    JdbcTemplate jdbc() {
        return jdbcTemplate;
    }
}
