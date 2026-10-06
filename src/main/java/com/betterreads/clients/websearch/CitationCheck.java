package com.betterreads.clients.websearch;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.betterreads.isbn.IsbnLanguage;

final class CitationCheck {

    private final SourcePageFetcher fetcher;

    private final Map<String, Optional<SourcePage>> pages = new HashMap<>();

    CitationCheck(final SourcePageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    CheckedBook verify(final FieldVerdicts verdicts, final MetadataCheckRequest asked) {
        final Map<String, FieldOutcome> outcomes = new LinkedHashMap<>(verdicts.outcomes());
        final AcceptedFields accepted = new AcceptedFields();
        verdicts.fields().forEach(verdict -> {
            final FieldOutcome outcome = check(verdict, asked);
            outcomes.put(verdict.field().key(), outcome);
            if (outcome == FieldOutcome.ACCEPTED) {
                accepted.add(verdict);
            }
        });
        if (accepted.universeWithoutSeries()) {
            outcomes.put(CheckedField.UNIVERSE.key(), FieldOutcome.VALUE_REJECTED);
        }
        final DescriptionVerdict description = verdicts.description();
        if (description.status() != DescriptionStatus.NOT_FOUND) {
            outcomes.put(MetadataCheckMapper.DESCRIPTION_FIELD, page(description.source())
                .map(page -> DescriptionCheck.check(description, page, asked, accepted))
                .orElse(FieldOutcome.PAGE_UNREACHABLE));
        }
        return new CheckedBook(accepted.metadata(), outcomes, verdicts.answer());
    }

    private FieldOutcome check(final FieldVerdict verdict, final MetadataCheckRequest asked) {
        final Optional<SourcePage> page = page(verdict.source());
        if (page.isEmpty()) {
            return FieldOutcome.PAGE_UNREACHABLE;
        }
        final Optional<FieldOutcome> unsupported =
            unsupported(page.get(), verdict.source(), verdict.quote(), QuoteMatch.valueTexts(verdict));
        if (unsupported.isPresent()) {
            return unsupported.get();
        }
        if (verdict.status() == VerdictStatus.CLEAR) {
            return QuoteMatch.namesTitle(verdict.quote(), asked)
                ? FieldOutcome.ACCEPTED
                : FieldOutcome.VALUE_NOT_IN_QUOTE;
        }
        if (!QuoteMatch.showsValue(verdict, asked)) {
            return FieldOutcome.VALUE_NOT_IN_QUOTE;
        }
        return bound(verdict.field(), page.get(), asked);
    }

    private static FieldOutcome bound(
        final CheckedField field, final SourcePage page, final MetadataCheckRequest asked) {
        final String stored = asked.isbn13();
        if (field == CheckedField.TITLE && stored != null && IsbnLanguage.isEnglish(stored)
            && !QuoteMatch.holdsIsbn(page.text(), stored)) {
            return FieldOutcome.TITLE_SOURCE_WITHOUT_ISBN;
        }
        if (field == CheckedField.ISBN && !QuoteMatch.namesTitle(page.heading(), asked)) {
            return FieldOutcome.TITLE_NOT_ON_PAGE;
        }
        return FieldOutcome.ACCEPTED;
    }

    private static Optional<FieldOutcome> unsupported(
        final SourcePage page, final String source, final String quote, final List<String> echoed) {
        if (!PageText.holds(page.text(), quote)) {
            return Optional.of(FieldOutcome.QUOTE_NOT_ON_PAGE);
        }
        final boolean echo = QuoteMatch.echoesUrl(source, echoed) || QuoteMatch.echoesUrl(page.url(), echoed);
        return echo ? Optional.of(FieldOutcome.QUOTE_IN_URL) : Optional.empty();
    }

    private Optional<SourcePage> page(final String url) {
        return pages.computeIfAbsent(url, fetcher::page);
    }
}
