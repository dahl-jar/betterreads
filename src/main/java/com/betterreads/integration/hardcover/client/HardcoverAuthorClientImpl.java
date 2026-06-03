package com.betterreads.integration.hardcover.client;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import com.betterreads.catalog.service.SourceAuthorWorks;
import com.betterreads.integration.hardcover.HardcoverAuthorClient;
import com.betterreads.integration.hardcover.dto.AuthorSearchDocument;
import com.betterreads.integration.hardcover.dto.AuthorWorksResponse;
import com.betterreads.integration.hardcover.dto.GraphQlRequest;
import com.betterreads.integration.hardcover.dto.TypesenseHits;
import com.betterreads.integration.hardcover.dto.TypesenseSearchResponse;
import com.betterreads.integration.hardcover.mapper.HardcoverAuthorMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Hardcover author client.
 *
 * <p>An Author search can return a co-author credit ahead of the author themselves, so the candidate
 * is the hit with the most books rather than the first. The chosen author id drives the works
 * enumeration, ordered by readers so the headline titles come first. A 401 means the token expired
 * or was revoked and resolves to empty; other 4xx resolve to empty; 5xx and network failures
 * propagate.
 */
@Component
public class HardcoverAuthorClientImpl implements HardcoverAuthorClient {

    private static final Logger LOG = LoggerFactory.getLogger(HardcoverAuthorClientImpl.class);

    private static final int WORKS_LIMIT = 60;

    private static final String SEARCH_QUERY = """
        query AuthorSearch($q: String!) {
          search(query: $q, query_type: "Author", per_page: 5, page: 1) { results }
        }
        """;

    private static final String WORKS_QUERY = """
        query AuthorWorks($id: Int!, $limit: Int!) {
          authors(where: {id: {_eq: $id}}) {
            name
            contributions(order_by: {book: {users_count: desc_nulls_last}}, limit: $limit) {
              book {
                id
                title
                description
                rating
                ratings_count
                users_count
                release_year
                canonical_id
                book_category_id
                compilation
                is_partial_book
                image { url }
                default_physical_edition { language { language } reading_format { format } }
                contributions { author { name } }
                book_series { position featured series { name } }
              }
            }
          }
        }
        """;

    private static final ParameterizedTypeReference<TypesenseSearchResponse<AuthorSearchDocument>>
        AUTHOR_HITS = new ParameterizedTypeReference<>() { };

    private static final Comparator<AuthorSearchDocument> BY_BOOKS =
        Comparator.comparingInt(HardcoverAuthorClientImpl::bookCount);

    private final WebClient hardcoverWebClient;

    private final HardcoverAuthorMapper mapper;

    public HardcoverAuthorClientImpl(
        final WebClient hardcoverWebClient,
        final HardcoverAuthorMapper mapper
    ) {
        this.hardcoverWebClient = hardcoverWebClient;
        this.mapper = mapper;
    }

    @Override
    public Optional<SourceAuthorWorks> fetchAuthorWorks(final String query) {
        return bestCandidate(query).flatMap(hit -> resolve(query, hit));
    }

    private Optional<SourceAuthorWorks> resolve(
        final String query,
        final AuthorSearchDocument hit
    ) {
        final Integer id = HardcoverGraphQl.parseId(hit.id());
        if (id == null) {
            return Optional.empty();
        }
        final AuthorWorksResponse.Author author = enumerate(query, id);
        return Optional.ofNullable(mapper.toSourceAuthorWorks(hit.name(), author));
    }

    private Optional<AuthorSearchDocument> bestCandidate(final String query) {
        try {
            final TypesenseSearchResponse<AuthorSearchDocument> response =
                hardcoverWebClient.post()
                    .bodyValue(new GraphQlRequest(SEARCH_QUERY, Map.of("q", query)))
                    .retrieve()
                    .bodyToMono(AUTHOR_HITS)
                    .block();
            return TypesenseHits.documents(response).stream()
                .filter(document -> document.name() != null)
                .max(BY_BOOKS);
        } catch (WebClientResponseException exception) {
            HardcoverGraphQl.recoverOrThrow(LOG, exception, query);
            return Optional.empty();
        }
    }

    private AuthorWorksResponse.@Nullable Author enumerate(final String query, final int id) {
        try {
            final AuthorWorksResponse response = hardcoverWebClient.post()
                .bodyValue(new GraphQlRequest(WORKS_QUERY, Map.of("id", id, "limit", WORKS_LIMIT)))
                .retrieve()
                .bodyToMono(AuthorWorksResponse.class)
                .block();
            return firstAuthor(response);
        } catch (WebClientResponseException exception) {
            HardcoverGraphQl.recoverOrThrow(LOG, exception, query);
            return null;
        }
    }

    private static AuthorWorksResponse.@Nullable Author firstAuthor(
        final @Nullable AuthorWorksResponse response
    ) {
        return Optional.ofNullable(response)
            .map(AuthorWorksResponse::data)
            .map(AuthorWorksResponse.Data::authors)
            .filter(list -> !list.isEmpty())
            .map(list -> list.get(0))
            .orElse(null);
    }

    private static int bookCount(final AuthorSearchDocument document) {
        final Integer count = document.booksCount();
        return count == null ? 0 : count;
    }
}
