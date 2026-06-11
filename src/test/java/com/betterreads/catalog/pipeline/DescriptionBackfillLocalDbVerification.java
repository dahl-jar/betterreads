package com.betterreads.catalog.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.refresh.DescriptionBackfillService;
import com.betterreads.catalog.repository.BookRepository;
import com.betterreads.catalog.repository.PendingBookRepository;
import com.betterreads.catalog.service.read.CatalogService;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.common.util.LogSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the description sweep against the live Wikipedia and Apple Books endpoints with the
 * locally-running compose Postgres, on books seeded with the bad description shapes production
 * carried: a Wikipedia publication-facts lead, an OpenLibrary markdown dump with series-navigation
 * trailers, a facts lead with a story fragment, and one good publisher blurb that must survive.
 *
 * <p>Skipped unless {@code RUN_LOCAL_DB_VERIFICATION=1}; opt-in because it writes to a real
 * database. Run with:
 * <pre>
 *   docker compose -f docker/docker-compose.yml up -d db
 *   source .env &amp;&amp; RUN_LOCAL_DB_VERIFICATION=1 ./gradlew test \
 *     --tests '*DescriptionBackfillLocalDbVerification*'
 * </pre>
 */
@SpringBootTest(properties = "betterreads.catalog.staging.poll-enabled=false")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_VERIFICATION", matches = "1")
// PMD.ClassNamingConventions: not *Test on purpose, an opt-in manual DB verification, not a CI test
@SuppressWarnings("PMD.ClassNamingConventions")
class DescriptionBackfillLocalDbVerification {

    private static final Logger LOG = LoggerFactory.getLogger(DescriptionBackfillLocalDbVerification.class);

    private static final int LOGGED_HEAD_LENGTH = 150;

    private static final int TWOK_YEAR = 2010;
    private static final int ASOS_YEAR = 2000;
    private static final int FOOL_YEAR = 1995;
    private static final int EYE_YEAR = 1990;

    private static final String TWOK_ISBN = "9780765326355";
    private static final String ASOS_ISBN = "9780593158968";
    private static final String FOOL_ISBN = "9780312119072";
    private static final String EYE_ISBN = "9780312850098";

    /** The Way of Kings as production carried it: the Wikipedia lead, publication facts only. */
    private static final String TWOK_FACTS =
        "The Way of Kings is an epic fantasy novel written by American author Brandon Sanderson "
        + "and the first book in The Stormlight Archive series. The novel was published on "
        + "August 31, 2010, by Tor Books. The Way of Kings consists of one prelude, one prologue, "
        + "75 chapters, an epilogue, and nine interludes. It was followed by Words of Radiance in "
        + "2014, Oathbringer in 2017, Rhythm of War in 2020 and Wind and Truth in 2024. A "
        + "leatherbound edition was released in 2021.";

    /** A Storm of Swords as OpenLibrary serves it: marketing preamble, bold heading, trailers. */
    private static final String ASOS_DUMP = """
        Here is the third volume in George R. R. Martin’s magnificent cycle of novels that \
        includes *A Game of Thrones* and *A Clash of Kings*. As a whole, this series comprises a \
        genuine masterpiece of modern fantasy, bringing together the best the genre has to offer.

        ***A Storm of Swords***

        Of the five contenders for power, one is dead, another in disfavor, and still the wars \
        rage as violently as ever, as alliances are made and broken. Joffrey, of House Lannister, \
        sits on the Iron Throne, the uneasy ruler of the land of the Seven Kingdoms.

        Preceded by: [***A Clash of Kings***][1]
        Followed by: [***A Feast for Crows***][2]

        [1]: https://openlibrary.org/works/OL257939W
        [2]: https://openlibrary.org/works/OL257948W""";

    /** To Play the Fool as production carried it: a facts lead with the story as a clause. */
    private static final String FOOL_FACTS =
        "To Play the Fool is the second book in the Kate Martinelli series by Laurie R. King. "
        + "Preceded by A Grave Talent and followed by the novel With Child, it describes the "
        + "investigation into the murder of a homeless man.";

    /** The Eye of the World's healthy publisher blurb, which the sweep must not degrade. */
    private static final String EYE_BLURB =
        "The Wheel of Time turns and Ages come and pass, leaving memories that become legend. "
        + "Legend fades to myth, and even myth is long forgotten when the Age that gave it birth "
        + "returns again. What was, what will be, and what is, may yet fall under the Shadow. "
        + "Moiraine Damodred arrives in Emond’s Field on a quest to find the one prophesized "
        + "to stand against The Dark One, a malicious entity sowing the seeds of chaos and "
        + "destruction.";

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private DescriptionBackfillService backfillService;

    @Autowired
    private BookRepository books;

    @Autowired
    private PendingBookRepository pendingBooks;

    @Test
    @DisplayName("the full sweep rewrites the bad description shapes and keeps the good blurb")
    void sweepRewritesBadDescriptionShapes() {
        pendingBooks.deleteAll();
        books.deleteAll();
        seed(seeded("The Way of Kings", "Brandon Sanderson", TWOK_ISBN, TWOK_YEAR)
            .wikidataQid("Q2136877").description(TWOK_FACTS).build());
        seed(seeded("A Storm of Swords", "George R. R. Martin", ASOS_ISBN, ASOS_YEAR)
            .description(ASOS_DUMP).build());
        seed(seeded("To Play the Fool", "Laurie R. King", FOOL_ISBN, FOOL_YEAR)
            .wikidataQid("Q17014108").description(FOOL_FACTS).build());
        seed(seeded("The Eye of the World", "Robert Jordan", EYE_ISBN, EYE_YEAR)
            .wikidataQid("Q477994").description(EYE_BLURB).build());

        backfillService.fullSweep();

        for (final Book book : books.findAll()) {
            LOG.info("catalog.localdb.description {} | {}",
                LogSanitizer.forLog(book.getTitle()),
                LogSanitizer.forLog(head(book.getDescription())));
        }

        assertThat(description(TWOK_ISBN))
            .as("the Way of Kings facts paragraph loses to a story blurb")
            .doesNotStartWith("The Way of Kings is an epic fantasy novel")
            .contains("Roshar");
        assertThat(description(ASOS_ISBN))
            .as("the Storm of Swords markdown dump loses to a story blurb, not store marketing")
            .doesNotContain("]: https://", "Apple Books", "enhanced edition")
            .contains("Joffrey");
        assertThat(description(FOOL_ISBN))
            .as("the To Play the Fool facts lead loses to a story blurb")
            .doesNotStartWith("To Play the Fool is the second book");
        assertThat(description(EYE_ISBN))
            .as("the healthy Eye of the World blurb survives the sweep")
            .startsWith("The Wheel of Time turns");
    }

    private void seed(final SourceBook book) {
        catalogService.upsertFromSource(book);
    }

    private static SourceBook.Builder seeded(
        final String title, final String author, final String isbn, final int year) {
        return SourceBook.builder(BookFieldSource.STAGED)
            .isbn13(isbn)
            .title(title)
            .authors(SourceAuthor.ofNames(List.of(author)))
            .publicationYear(year);
    }

    private String description(final String isbn) {
        return books.findAll().stream()
            .filter(book -> isbn.equals(book.getIsbn()))
            .findFirst()
            .orElseThrow()
            .getDescription();
    }

    private static String head(final String description) {
        return description.substring(0, Math.min(LOGGED_HEAD_LENGTH, description.length()));
    }
}
