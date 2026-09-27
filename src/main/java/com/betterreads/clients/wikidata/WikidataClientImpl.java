package com.betterreads.clients.wikidata;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.text.TextMatch;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Search ranks films and franchises above the work, so a title search keeps the first candidate that
 * is a written work and lists the requested author. Wikidata holds no ISBN on works, so an ISBN
 * lookup returns empty.
 */
@Component
class WikidataClientImpl implements WikidataClient {

    private static final String INSTANCE_OF_PROPERTY = "P31";

    private static final String LITERARY_WORK = "Q7725634";
    private static final String WRITTEN_WORK = "Q47461344";
    private static final String COMIC_BOOK_SERIES = "Q14406742";
    private static final String LIMITED_SERIES = "Q3297186";

    private static final Set<String> WRITTEN_WORK_TYPES =
        Set.of(LITERARY_WORK, WRITTEN_WORK, COMIC_BOOK_SERIES, LIMITED_SERIES);

    private final WikidataApi api;

    private final WikidataMapper mapper;

    public WikidataClientImpl(final WikidataApi api, final WikidataMapper mapper) {
        this.api = api;
        this.mapper = mapper;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.WIKIDATA;
    }

    @Override
    public Optional<SourceBook> fetchByIsbn(final String isbn) {
        return Optional.empty();
    }

    @Override
    public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
        return api.searchCandidates(title).stream()
            .flatMap(qid -> resolveWork(qid, title, author).stream())
            .findFirst();
    }

    private Optional<SourceBook> resolveWork(final String qid, final String title, final String author) {
        return api.entity(qid)
            .filter(WikidataClientImpl::isWrittenWork)
            .filter(entity -> titleMatches(entity, qid, title))
            .flatMap(entity -> toSourceBook(entity, qid, author));
    }

    private Optional<SourceBook> toSourceBook(final JsonNode entity, final String qid, final String author) {
        final List<SourceAuthor> authors = WikidataTree.entityIds(entity, WikidataTree.AUTHOR_PROPERTY).stream()
            .map(this::fetchAuthor)
            .filter(candidate -> candidate != null)
            .toList();
        if (authors.stream().noneMatch(candidate -> TextMatch.eitherContainsIgnoreCase(candidate.name(), author))) {
            return Optional.empty();
        }
        return mapper.toSourceBook(entity, qid, this::resolveLabel)
            .map(book -> book.toBuilder().authors(authors).build());
    }

    private @Nullable SourceAuthor fetchAuthor(final String authorQid) {
        return api.entity(authorQid)
            .map(authorEntity -> WikidataAuthors.fromEntity(authorEntity, authorQid))
            .orElse(null);
    }

    private @Nullable String resolveLabel(final String qid) {
        return api.entity(qid)
            .map(WikidataLabels::displayName)
            .orElse(null);
    }

    private static boolean isWrittenWork(final JsonNode entity) {
        return WikidataTree.entityIds(entity, INSTANCE_OF_PROPERTY).stream()
            .anyMatch(WRITTEN_WORK_TYPES::contains);
    }

    private static boolean titleMatches(final JsonNode entity, final String qid, final String title) {
        final String entityTitle = WikidataLabels.displayName(entity);
        if (entityTitle == null || entityTitle.equals(qid)) {
            return false;
        }
        return TextMatch.eitherContainsIgnoreCase(entityTitle, title);
    }
}
