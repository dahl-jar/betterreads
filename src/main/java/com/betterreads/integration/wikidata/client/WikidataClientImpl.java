package com.betterreads.integration.wikidata.client;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.model.SourceAuthor;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.common.util.TextMatch;
import com.betterreads.integration.wikidata.WikidataApi;
import com.betterreads.integration.wikidata.WikidataClient;
import com.betterreads.integration.wikidata.mapper.WikidataAuthors;
import com.betterreads.integration.wikidata.mapper.WikidataClaims;
import com.betterreads.integration.wikidata.mapper.WikidataMapper;
import com.betterreads.integration.wikidata.mapper.WikidataTree;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Wikidata client over the entity search and entity document endpoints.
 *
 * <p>Search ranks films and franchises above the work, so {@code fetchByTitleAuthor} keeps the first
 * candidate that is a written-work type and lists the requested author. Wikidata holds no ISBN on
 * works, so {@code fetchByIsbn} returns empty.
 */
@Component
public class WikidataClientImpl implements WikidataClient {

    private static final String AUTHOR_PROPERTY = "P50";
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

    @Override
    public Optional<SourceBook> fetchByQid(final String qid) {
        return api.entity(qid)
            .filter(WikidataClientImpl::isWrittenWork)
            .flatMap(entity -> toSourceBook(entity, qid));
    }

    private Optional<SourceBook> resolveWork(final String qid, final String title, final String author) {
        return api.entity(qid)
            .filter(WikidataClientImpl::isWrittenWork)
            .filter(entity -> titleMatches(entity, qid, title))
            .filter(entity -> hasAuthor(entity, author))
            .flatMap(entity -> toSourceBook(entity, qid));
    }

    private Optional<SourceBook> toSourceBook(final JsonNode entity, final String qid) {
        return mapper.toSourceBook(entity, qid, this::resolveLabel)
            .map(book -> withAuthorsFromEntities(book, entity));
    }

    private SourceBook withAuthorsFromEntities(final SourceBook book, final JsonNode entity) {
        final List<SourceAuthor> authors = WikidataTree.entityIds(entity, AUTHOR_PROPERTY).stream()
            .map(this::fetchAuthor)
            .filter(author -> author != null)
            .toList();
        if (authors.isEmpty()) {
            return book;
        }
        return book.toBuilder().authors(authors).build();
    }

    private @Nullable SourceAuthor fetchAuthor(final String authorQid) {
        return api.entity(authorQid)
            .map(authorEntity -> WikidataAuthors.fromEntity(authorEntity, authorQid))
            .orElse(null);
    }

    private @Nullable String resolveLabel(final String qid) {
        return api.entity(qid)
            .map(WikidataClaims::label)
            .orElse(null);
    }

    private static boolean isWrittenWork(final JsonNode entity) {
        return WikidataTree.entityIds(entity, INSTANCE_OF_PROPERTY).stream()
            .anyMatch(WRITTEN_WORK_TYPES::contains);
    }

    private static boolean titleMatches(final JsonNode entity, final String qid, final String title) {
        final String entityTitle = WikidataClaims.label(entity);
        if (entityTitle == null || entityTitle.equals(qid)) {
            return false;
        }
        return TextMatch.containsIgnoreCase(entityTitle, title)
            || TextMatch.containsIgnoreCase(title, entityTitle);
    }

    private boolean hasAuthor(final JsonNode entity, final String author) {
        return WikidataTree.entityIds(entity, AUTHOR_PROPERTY).stream()
            .map(this::resolveLabel)
            .filter(name -> name != null)
            .anyMatch(name -> TextMatch.containsIgnoreCase(name, author)
                || TextMatch.containsIgnoreCase(author, name));
    }
}
