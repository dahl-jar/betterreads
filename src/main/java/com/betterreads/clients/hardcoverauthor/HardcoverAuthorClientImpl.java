package com.betterreads.clients.hardcoverauthor;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.betterreads.booksource.SourceAuthorWorks;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverGraphQlRequest;
import com.betterreads.clients.hardcover.HardcoverGraphQl;
import com.betterreads.clients.hardcover.TypesenseSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** An Author search can rank a co-author credit above the author, so the hit with the most books wins. */
@Component
class HardcoverAuthorClientImpl implements HardcoverAuthorClient {

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
            contributions(order_by: {book: {users_count: desc_nulls_last}}, limit: $limit) {
              book {
        """ + HardcoverBookNode.FIELDS + """
              }
            }
          }
        }
        """;

    private static final ParameterizedTypeReference<TypesenseSearchResponse<AuthorSearchDocument>>
        AUTHOR_HITS = new ParameterizedTypeReference<>() { };

    private static final ParameterizedTypeReference<AuthorWorksResponse> WORKS =
        new ParameterizedTypeReference<>() { };

    private static final Comparator<AuthorSearchDocument> BY_BOOKS =
        Comparator.comparingInt(document -> Objects.requireNonNullElse(document.booksCount(), 0));

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
        return HardcoverGraphQl.parseId(hit.id())
            .flatMap(id -> authorWorks(query, id))
            .map(author -> mapper.toSourceAuthorWorks(hit.name(), author));
    }

    private Optional<AuthorSearchDocument> bestCandidate(final String query) {
        return HardcoverGraphQl.search(hardcoverWebClient, LOG, SEARCH_QUERY, query, AUTHOR_HITS).stream()
            .filter(document -> document.name() != null)
            .max(BY_BOOKS);
    }

    private Optional<AuthorWorksResponse.Author> authorWorks(final String query, final int id) {
        return HardcoverGraphQl.post(hardcoverWebClient, LOG,
                new HardcoverGraphQlRequest(WORKS_QUERY, Map.of("id", id, "limit", WORKS_LIMIT)),
                WORKS, query)
            .map(AuthorWorksResponse::data)
            .map(AuthorWorksResponse.Data::authors)
            .flatMap(authors -> authors.stream().findFirst());
    }
}
