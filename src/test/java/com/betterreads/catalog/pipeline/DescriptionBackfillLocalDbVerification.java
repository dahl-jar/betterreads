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
import com.betterreads.common.util.EnglishText;
import com.betterreads.common.util.LogSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the description sweep against the live description sources with the locally-running compose
 * Postgres, on books seeded with the descriptions production's top-rated list carried on
 * 2026-06-13: a French Apple Books blurb, three Wikipedia publication-facts leads, an
 * illustrated-edition marketing lead, an OpenLibrary facts lead, and three healthy story blurbs
 * that must stay story-shaped.
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

    private static final int MIN_HEALTHY_LENGTH = 200;

    private static final int TWOK_YEAR = 2010;
    private static final int ASOS_YEAR = 2000;
    private static final int FOOL_YEAR = 1995;
    private static final int EYE_YEAR = 1990;
    private static final int WOTM_YEAR = 2023;
    private static final int LOST_METAL_YEAR = 2022;
    private static final int SOUL_YEAR = 2012;
    private static final int KENNEDY_YEAR = 2011;
    private static final int WOR_YEAR = 2014;
    private static final int MISTBORN_YEAR = 2006;

    private static final String SANDERSON = "Brandon Sanderson";

    private static final String WOR_TITLE = "Words of Radiance";

    private static final String EYE_TITLE = "The Eye of the World";

    private static final String TWOK_ISBN = "9780765326355";
    private static final String ASOS_ISBN = "9780593158968";
    private static final String FOOL_ISBN = "9780312119072";
    private static final String EYE_ISBN = "9780312850098";
    private static final String WOTM_ISBN = "9781982141172";
    private static final String LOST_METAL_ISBN = "9780765391193";
    private static final String SOUL_ISBN = "9781616961510";
    private static final String KENNEDY_ISBN = "9781451627282";
    private static final String WOR_ISBN = "9780765326362";
    private static final String MISTBORN_ISBN = "9780765311788";

    /** The Way of Kings as production carried it: the Wikipedia lead, publication facts only. */
    private static final String TWOK_FACTS =
        "The Way of Kings is an epic fantasy novel written by American author Brandon Sanderson "
        + "and the first book in The Stormlight Archive series. The novel was published on "
        + "August 31, 2010, by Tor Books. The Way of Kings consists of one prelude, one prologue, "
        + "75 chapters, an epilogue, and nine interludes. It was followed by Words of Radiance in "
        + "2014, Oathbringer in 2017, Rhythm of War in 2020 and Wind and Truth in 2024. A "
        + "leatherbound edition was released in 2021.";

    /** A Storm of Swords as production serves it: illustrated-edition marketing around the story. */
    private static final String ASOS_MARKETING =
        "In this gorgeous illustrated edition of the third book in the landmark A Song of Ice and "
        + "Fire series, acclaimed artist Gary Gianni brings the action to life with twenty-five "
        + "all-new illustrations in both color and black-and-white—for fans of HBO's Game of "
        + "ThronesOf the five contenders for power, one is dead, another in disfavor, and still "
        + "the wars rage. Joffrey sits on the Iron Throne, the uneasy ruler of the Seven Kingdoms. "
        + "His most bitter rival, Lord Stannis, stands defeated and disgraced. Young Robb still "
        + "rules the North from the fortress of Riverrun. Meanwhile, making her way across a "
        + "blood-drenched continent is the exiled queen, Daenerys, mistress of the only three "
        + "dragons left in the world. As the future of the land hangs in the balance, no one will "
        + "rest until the Seven Kingdoms have exploded in a veritable storm of swords. Explore the "
        + "illustrated editions of A Song of Ice and Fire.";

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

    /** The Will of the Many as production served it: the French edition's Apple Books blurb. */
    private static final String WOTM_FRENCH =
        "Sélection Prix Babelio 2026 L’événement Fantasy enfin traduit en français Ils "
        + "m’appellent Vis Telimus. Ils croient que j’ai eu la chance d’être adopté par un "
        + "sénateur et envoyé à l’Académie pour rejoindre l’élite. Celle-ci exploite l’énergie "
        + "mentale des castes inférieures, leur Volonté, pour se doter de talents extraordinaires "
        + "et maintenir l’ordre social. Ainsi la Hiérarchie a-t-elle conquis le monde. Mais la "
        + "vérité, c'est que je suis venu résoudre un meurtre. Chercher une arme légendaire. "
        + "Révéler des secrets qui pourraient déchirer la République. Jamais je ne céderai ma "
        + "Volonté à ceux qui ont exécuté ma véritable famille. Le best-seller de James "
        + "Islington, enfin traduit en français, réunit avec une maîtrise époustouflante un "
        + "univers profond et cohérent et d’incroyables révélations finales!";

    /** The Lost Metal as production carried it: the Wikipedia lead, publication facts only. */
    private static final String LOST_METAL_FACTS =
        "Mistborn: The Lost Metal is an urban fantasy novel written by American author Brandon "
        + "Sanderson. It was published on November 15, 2022, by Tor Books. It is the fourth and "
        + "final book in the Wax and Wayne series and seventh in the Mistborn series. It is "
        + "preceded by The Bands of Mourning in 2016 and is to be followed by a new trilogy, "
        + "written after release of a novelization of the Cosmere graphic novel White Sand.";

    /** The Emperor's Soul as production carried it: the Wikipedia lead, publication facts only. */
    private static final String SOUL_FACTS =
        "The Emperor's Soul is a fantasy novella written by American author Brandon Sanderson. "
        + "It was first published in November 2012 by Tachyon Publications. It won the 2013 Hugo "
        + "Award for best novella. The novella is included in the 2016 Arcanum Unbounded: The "
        + "Cosmere Collection.";

    /** 11/22/63 as production carried it: the Wikipedia lead, facts with the plot as a clause. */
    private static final String KENNEDY_FACTS =
        "11/22/63 is a novel by American author Stephen King about a time traveler who attempts "
        + "to prevent the assassination of United States President John F. Kennedy, which "
        + "occurred on November 22, 1963. It is the 60th book published by Stephen King, his 49th "
        + "novel, and the 42nd under his own name. The novel required considerable research to "
        + "accurately portray the late 1950s and early 1960s.";

    /** Words of Radiance's healthy story blurb, which must stay story-shaped after the sweep. */
    private static final String WOR_BLURB =
        "Expected by his enemies to die the miserable death of a military slave, Kaladin "
        + "survived to be given command of the royal bodyguards, a controversial first for a "
        + "low-status \"darkeyes.\" Now he must protect the king and Dalinar from every common "
        + "peril as well as the distinctly uncommon threat of the Assassin, all while secretly "
        + "struggling to master remarkable new powers that are somehow linked to his honorspren, "
        + "Syl. The Assassin, Szeth, is active again, murdering rulers all over the world of "
        + "Roshar, using his baffling powers to thwart every bodyguard and elude all pursuers.";

    /** Mistborn's healthy story blurb, which must stay story-shaped after the sweep. */
    private static final String MISTBORN_BLURB =
        "For a thousand years the ash fell and no flowers bloomed. For a thousand years the Skaa "
        + "slaved in misery and lived in fear. For a thousand years the Lord Ruler, the \"Sliver "
        + "of Infinity,\" reigned with absolute power and ultimate terror, divinely invincible. "
        + "Then, when hope was so long lost that not even its memory remained, a terribly "
        + "scarred, heart-broken half-Skaa rediscovered it in the depths of the Lord Ruler's most "
        + "hellish prison. Kelsier \"snapped\" and found in himself the powers of a Mistborn. A "
        + "brilliant thief and natural leader, he turned his talents to the ultimate caper, with "
        + "the Lord Ruler himself as the mark.";

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private DescriptionBackfillService backfillService;

    @Autowired
    private BookRepository books;

    @Autowired
    private PendingBookRepository pendingBooks;

    // PMD.UnitTestContainsTooManyAsserts: one sweep over ten seeded books, one assert block per book
    @SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
    @Test
    @DisplayName("the full sweep rewrites every bad top-rated description and keeps the healthy ones story-shaped")
    void sweepRewritesBadDescriptionShapes() {
        seedTopRatedBooks();

        backfillService.fullSweep();

        for (final Book book : books.findAll()) {
            LOG.info("catalog.localdb.description {} | {}",
                LogSanitizer.forLog(book.getTitle()),
                LogSanitizer.forLog(head(book.getDescription())));
        }

        assertThat(description(WOTM_ISBN))
            .as("the French Apple Books blurb is replaced by an English story blurb")
            .doesNotContain("Babelio", "traduit en français")
            .contains("Vis");
        assertThat(EnglishText.isEnglish(description(WOTM_ISBN)))
            .as("the Will of the Many description ends up in English")
            .isTrue();
        assertThat(description(LOST_METAL_ISBN))
            .as("the Lost Metal facts paragraph loses to a story blurb")
            .doesNotStartWith("Mistborn: The Lost Metal is an urban fantasy novel")
            .contains("Waxillium");
        assertThat(description(SOUL_ISBN))
            .as("the Emperor's Soul facts paragraph loses to a story blurb")
            .doesNotStartWith("The Emperor's Soul is a fantasy novella")
            .contains("Shai");
        assertThat(description(KENNEDY_ISBN))
            .as("the 11/22/63 facts paragraph loses to a story blurb")
            .doesNotContain("is a novel by American author")
            .contains("Kennedy");
        assertThat(description(TWOK_ISBN))
            .as("the Way of Kings facts paragraph loses to a story blurb")
            .doesNotStartWith("The Way of Kings is an epic fantasy novel")
            .contains("Roshar");
        assertThat(description(ASOS_ISBN))
            .as("the Storm of Swords edition marketing loses to a story blurb")
            .doesNotContain("illustrated edition", "]: https://", "Apple Books")
            .contains("Joffrey");
        assertThat(description(FOOL_ISBN))
            .as("the To Play the Fool facts lead loses to a story blurb")
            .doesNotStartWith("To Play the Fool is the second book");
        assertHealthyStoryBlurb(EYE_TITLE, EYE_ISBN, "Moiraine", "Rand", "Emond");
        assertHealthyStoryBlurb(WOR_TITLE, WOR_ISBN, "Kaladin", "Shallan", "Szeth", "Dalinar");
        assertHealthyStoryBlurb("Mistborn", MISTBORN_ISBN, "Kelsier", "Vin", "Skaa", "Lord Ruler");
    }

    private void seedTopRatedBooks() {
        pendingBooks.deleteAll();
        books.deleteAll();
        seed(seeded("The Way of Kings", SANDERSON, TWOK_ISBN, TWOK_YEAR)
            .wikidataQid("Q2136877").openLibraryWorkKey("OL15358691W").hardcoverId("386446")
            .description(TWOK_FACTS).build());
        seed(seeded("A Storm of Swords", "George R. R. Martin", ASOS_ISBN, ASOS_YEAR)
            .openLibraryWorkKey("OL257914W").hardcoverId("236")
            .description(ASOS_MARKETING).build());
        seed(seeded("To Play the Fool", "Laurie R. King", FOOL_ISBN, FOOL_YEAR)
            .wikidataQid("Q17014108").description(FOOL_FACTS).build());
        seed(seeded(EYE_TITLE, "Robert Jordan", EYE_ISBN, EYE_YEAR)
            .wikidataQid("Q477994").description(EYE_BLURB).build());
        seed(seeded("The Will of the Many", "James Islington", WOTM_ISBN, WOTM_YEAR)
            .wikidataQid("Q124153740").openLibraryWorkKey("OL31088394W").hardcoverId("594985")
            .description(WOTM_FRENCH).build());
        seed(seeded("The Lost Metal", SANDERSON, LOST_METAL_ISBN, LOST_METAL_YEAR)
            .wikidataQid("Q111635058").openLibraryWorkKey("OL26769924W").hardcoverId("427863")
            .description(LOST_METAL_FACTS).build());
        seed(seeded("The Emperor's Soul", SANDERSON, SOUL_ISBN, SOUL_YEAR)
            .wikidataQid("Q17060438").openLibraryWorkKey("OL19960448W").hardcoverId("427626")
            .description(SOUL_FACTS).build());
        seed(seeded("11/22/63", "Stephen King", KENNEDY_ISBN, KENNEDY_YEAR)
            .wikidataQid("Q723020").openLibraryWorkKey("OL16002468W").hardcoverId("383587")
            .description(KENNEDY_FACTS).build());
        seed(seeded(WOR_TITLE, SANDERSON, WOR_ISBN, WOR_YEAR)
            .wikidataQid("Q8034469").openLibraryWorkKey("OL16813053W").hardcoverId("374131")
            .description(WOR_BLURB).build());
        seed(seeded("Mistborn: The Final Empire", SANDERSON, MISTBORN_ISBN, MISTBORN_YEAR)
            .wikidataQid("Q2778373").openLibraryWorkKey("OL16044142W").hardcoverId("369692")
            .description(MISTBORN_BLURB).build());
    }

    private void assertHealthyStoryBlurb(
        final String title, final String isbn, final String... characterNames) {
        final String description = description(isbn);
        assertThat(EnglishText.isEnglish(description))
            .as("%s stays English", title)
            .isTrue();
        assertThat(description.length())
            .as("%s keeps a description of real length", title)
            .isGreaterThanOrEqualTo(MIN_HEALTHY_LENGTH);
        assertThat(description)
            .as("%s stays a story blurb about its own characters", title)
            .containsAnyOf(characterNames)
            .doesNotContain("ebundle", "discounted", "is an epic fantasy novel written by",
                "Prime Video", "major motion picture");
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
