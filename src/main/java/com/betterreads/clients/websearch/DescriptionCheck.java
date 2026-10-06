package com.betterreads.clients.websearch;

import java.util.List;
import java.util.Optional;

import com.betterreads.book.FieldEvidence;
import com.betterreads.bookdescription.DescriptionQuality;
import org.jspecify.annotations.Nullable;

final class DescriptionCheck {

    private static final int MAX_DESCRIPTION_LENGTH = 2400;

    private static final int MIN_KEEP_WORDS = 6;

    private static final String ANCHOR_JOIN = " ... ";

    private DescriptionCheck() {
    }

    static FieldOutcome check(final DescriptionVerdict verdict, final SourcePage page,
        final MetadataCheckRequest asked, final AcceptedFields accepted) {
        return unbound(verdict, page, asked).orElseGet(() -> switch (verdict.status()) {
            case KEEP -> keep(verdict, page, asked.description(), accepted);
            case CLEAR -> clear(verdict, page, asked, accepted);
            case REPLACE, REPAIR -> replace(verdict, page, accepted);
            case NOT_FOUND -> FieldOutcome.NOT_ANSWERED;
        });
    }

    private static Optional<FieldOutcome> unbound(
        final DescriptionVerdict verdict, final SourcePage page, final MetadataCheckRequest asked) {
        final List<String> echoed = List.of(verdict.quote(), verdict.start(), verdict.end());
        if (QuoteMatch.echoesUrl(verdict.source(), echoed) || QuoteMatch.echoesUrl(page.url(), echoed)) {
            return Optional.of(FieldOutcome.QUOTE_IN_URL);
        }
        return QuoteMatch.namesTitle(page.heading(), asked)
            ? Optional.empty()
            : Optional.of(FieldOutcome.TITLE_NOT_ON_PAGE);
    }

    private static FieldOutcome keep(final DescriptionVerdict verdict, final SourcePage page,
        final @Nullable String stored, final AcceptedFields accepted) {
        if (!PageText.holds(page.text(), verdict.quote())) {
            return FieldOutcome.QUOTE_NOT_ON_PAGE;
        }
        if (PageText.wordCount(verdict.quote()) < MIN_KEEP_WORDS) {
            return FieldOutcome.QUOTE_TOO_SHORT;
        }
        if (stored == null || !PageText.hasWords(stored, verdict.quote())) {
            return FieldOutcome.QUOTE_NOT_STORED;
        }
        accepted.storeDescription(stored, evidence(verdict, verdict.quote()));
        return FieldOutcome.KEPT;
    }

    private static FieldOutcome replace(
        final DescriptionVerdict verdict, final SourcePage page, final AcceptedFields accepted) {
        final Optional<String> cut = PageText.cut(page.text(), verdict.start(), verdict.end());
        if (cut.isEmpty()) {
            return FieldOutcome.ANCHOR_NOT_FOUND;
        }
        final String text = cut.get();
        if (text.length() > MAX_DESCRIPTION_LENGTH || !DescriptionQuality.assess(text).usable()) {
            return FieldOutcome.CUT_REJECTED;
        }
        accepted.storeDescription(text, evidence(verdict, verdict.start() + ANCHOR_JOIN + verdict.end()));
        return FieldOutcome.ACCEPTED;
    }

    private static FieldOutcome clear(final DescriptionVerdict verdict, final SourcePage page,
        final MetadataCheckRequest asked, final AcceptedFields accepted) {
        if (!PageText.holds(page.text(), verdict.quote())) {
            return FieldOutcome.QUOTE_NOT_ON_PAGE;
        }
        if (!QuoteMatch.namesTitle(verdict.quote(), asked)) {
            return FieldOutcome.VALUE_NOT_IN_QUOTE;
        }
        accepted.clearDescription(evidence(verdict, verdict.quote()));
        return FieldOutcome.ACCEPTED;
    }

    private static FieldEvidence evidence(final DescriptionVerdict verdict, final String quote) {
        return new FieldEvidence(verdict.source(), quote);
    }
}
