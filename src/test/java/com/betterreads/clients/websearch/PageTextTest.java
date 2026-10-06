package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PageTextTest {

    private static final String END_ANCHOR = "ends here.";

    private static final String PAGE = "Menu. Darrow’s  world — Mars burns. Other books by Pierce Brown";

    private static final String MARS_BURNS = "mars burns.";

    private static final String ELLIPSES = "Wait… then… Mars burns.";

    @Nested
    class Matches {

        @Test
        void shouldMatchAQuoteAcrossCurlyQuotesAndDashes() {
            final boolean held = PageText.holds(PAGE, "Darrow's world - Mars burns.");

            assertThat(held).isTrue();
        }

        @Test
        void shouldMatchAcrossCollapsedWhitespaceAndCase() {
            final String page = "Red   Rising\n\tis a NOVEL";

            final boolean held = PageText.holds(page, "red rising is a novel");

            assertThat(held).isTrue();
        }

        @Test
        void shouldRejectAQuoteThatIsNotOnThePage() {
            final boolean held = PageText.holds(PAGE, "Darrow's world - Venus burns.");

            assertThat(held).isFalse();
        }

        @Test
        void shouldMatchAQuoteWithLeadingWhitespaceAtThePageStart() {
            final boolean held = PageText.holds("Mars burns tonight", "  Mars burns");

            assertThat(held).isTrue();
        }

        @Test
        void shouldMatchAQuoteWrittenWithoutAccents() {
            final boolean held = PageText.holds("A café on Mars", "a cafe on mars");

            assertThat(held).isTrue();
        }

        @Test
        void shouldRejectAnEmptyQuote() {
            final boolean held = PageText.holds(PAGE, " ");

            assertThat(held).isFalse();
        }

        @Test
        void shouldMatchAQuoteOnAPageWithEllipses() {
            final boolean held = PageText.holds(ELLIPSES, "wait... then... mars");

            assertThat(held).isTrue();
        }

        @Test
        void shouldMatchWholeWordsOnly() {
            final boolean held = PageText.hasWords("first published in 2014", "201");

            assertThat(held).isFalse();
        }

        @Test
        void shouldMatchInitialsWrittenWithOrWithoutSpaces() {
            final boolean held = PageText.hasWords("The Hobbit by J. R. R. Tolkien", "J.R.R. Tolkien");

            assertThat(held).isTrue();
        }

        @Test
        void shouldFindNoWordsInAnEmptyPhrase() {
            final boolean held = PageText.hasWords("The Hobbit", "...");

            assertThat(held).isFalse();
        }
    }

    @Nested
    class Cuts {

        @Test
        void shouldCutTheOriginalTextBetweenAnchors() {
            final Optional<String> cut = PageText.cut(PAGE, "darrow's world", MARS_BURNS);

            assertThat(cut).contains("Darrow’s  world — Mars burns.");
        }

        @Test
        void shouldStopTheCutAtTheEndAnchor() {
            final String page = "Intro. The start of it ends here. More text ends here.";

            final Optional<String> cut = PageText.cut(page, "The start", END_ANCHOR);

            assertThat(cut).contains("The start of it ends here.");
        }

        @Test
        void shouldFindNoCutWhenTheEndAnchorPrecedesTheStart() {
            final String page = "It ends here. Then the start comes.";

            final Optional<String> cut = PageText.cut(page, "the start", END_ANCHOR);

            assertThat(cut).isEmpty();
        }

        @Test
        void shouldSearchForTheEndAnchorAfterTheStartAnchor() {
            final String page = "The end. Credits roll. The end.";

            final Optional<String> cut = PageText.cut(page, "The end.", "end.");

            assertThat(cut).contains(page);
        }

        @Test
        void shouldCutTheOriginalCharactersAcrossAnEllipsis() {
            final String page = "Menu. " + ELLIPSES + " Footer";

            final Optional<String> cut = PageText.cut(page, "wait...", MARS_BURNS);

            assertThat(cut).contains(ELLIPSES);
        }
    }
}
