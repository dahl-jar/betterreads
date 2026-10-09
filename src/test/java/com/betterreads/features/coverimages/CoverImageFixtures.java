package com.betterreads.features.coverimages;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import javax.imageio.ImageIO;

import com.betterreads.book.Book;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CoverSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.images.CoverFetcher;
import com.betterreads.images.Image;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

final class CoverImageFixtures {

    static final String APPLE_COVER = "https://is1-ssl.mzstatic.com/a/3000x3000bb.jpg";

    static final String HARDCOVER_COVER = "https://hardcover.example.test/1.jpg";

    static final String GOOGLE_COVER = "https://books.google.com/books/content?id=1";

    static final String OPEN_LIBRARY_COVER = "https://openlibrary.example.test/1-L.jpg";

    static final String ISBN = "9780765326355";

    static final long BOOK_ID = 1L;

    static final String KEY = "OL1W";

    static final String COVER_URL = "https://covers.example.org/1.jpg";

    static final String STORE = "https://books.apple.com/us/book/the-way-of-kings/id1";

    static final CoverCandidate APPLE_CANDIDATE = new CoverCandidate(CoverSource.APPLE_BOOKS, APPLE_COVER, STORE);

    static final CoverCandidate HARDCOVER_CANDIDATE = new CoverCandidate(CoverSource.HARDCOVER, HARDCOVER_COVER, null);

    static final CoverCandidate OPEN_LIBRARY_CANDIDATE =
        new CoverCandidate(CoverSource.OPEN_LIBRARY, OPEN_LIBRARY_COVER, null);

    private static final int GRAY_LEVELS = 256;

    private static final int GRAY_STEP = 7;

    private static final int RED_SHIFT = 16;

    private static final int GREEN_SHIFT = 8;

    private CoverImageFixtures() {
    }

    static SourceBook.Builder wayOfKings(final @Nullable String coverUrl) {
        return SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .title("The Way of Kings")
            .isbn13(ISBN)
            .coverUrl(coverUrl);
    }

    static Book book(final long bookId, final @Nullable String coverUrl) {
        final Book book = new Book();
        book.setBookId(bookId);
        book.applyFrom(wayOfKings(coverUrl).build());
        return book;
    }

    static Book book(final long bookId) {
        final Book book = new Book();
        book.setBookId(bookId);
        book.setDedupKey(KEY);
        book.setCoverUrl(COVER_URL);
        return book;
    }

    static CoverFetcher failingFetcher(final Set<String> failing) {
        return url -> {
            if (failing.contains(url)) {
                throw WebClientResponseException.create(
                    HttpStatus.BAD_GATEWAY.value(), "bad gateway", null, null, null);
            }
            return Optional.of(image(url));
        };
    }

    static Image image(final String url) {
        return new Image(bytes(url), "image/jpeg");
    }

    static byte[] notImage() {
        return "<html>not found</html>".getBytes(StandardCharsets.UTF_8);
    }

    static byte[] bytes(final String url) {
        return url.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] patterned(final int width, final int height) {
        return gray(width, height, (x, y) -> (x + y) * GRAY_STEP % GRAY_LEVELS);
    }

    static byte[] gray(final int width, final int height, final IntBinaryOperator level) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int gray = level.applyAsInt(x, y);
                image.setRGB(x, y, gray << RED_SHIFT | gray << GREEN_SHIFT | gray);
            }
        }
        return png(image);
    }

    static byte[] blank(final int width, final int height) {
        return png(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
    }

    static byte[] png(final BufferedImage image) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }
}
