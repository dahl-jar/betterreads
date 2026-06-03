package com.betterreads.integration.hardcover.client;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import com.betterreads.catalog.service.SourceSeries;
import com.betterreads.integration.hardcover.HardcoverSeriesClient;
import com.betterreads.integration.hardcover.dto.GraphQlRequest;
import com.betterreads.integration.hardcover.dto.SeriesEnumerationResponse;
import com.betterreads.integration.hardcover.dto.SeriesSearchDocument;
import com.betterreads.integration.hardcover.dto.TypesenseHits;
import com.betterreads.integration.hardcover.dto.TypesenseSearchResponse;
import com.betterreads.integration.hardcover.mapper.HardcoverSeriesMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Hardcover series client.
 *
 * <p>A Series search ranks by relevance, not series size, and a parody or fan series can outrank the
 * real one, so the candidate is the hit with the most readers rather than the first. The chosen
 * series id then drives the volume enumeration. A 401 means the token expired or was revoked and
 * resolves to empty; other 4xx resolve to empty; 5xx and network failures propagate.
 */
@Component
public class HardcoverSeriesClientImpl implements HardcoverSeriesClient {

    private static final Logger LOG = LoggerFactory.getLogger(HardcoverSeriesClientImpl.class);

    private static final String SEARCH_QUERY = """
        query SeriesSearch($q: String!) {
          search(query: $q, query_type: "Series", per_page: 5, page: 1) { results }
        }
        """;

    private static final String ENUM_QUERY = """
        query SeriesEnum($id: Int!) {
          series(where: {id: {_eq: $id}}) {
            name
            primary_books_count
            book_series(order_by: {position: asc}) {
              position
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
              }
            }
          }
        }
        """;

    private static final ParameterizedTypeReference<TypesenseSearchResponse<SeriesSearchDocument>>
        SERIES_HITS = new ParameterizedTypeReference<>() { };

    private static final Comparator<SeriesSearchDocument> BY_READERS =
        Comparator.comparingInt(HardcoverSeriesClientImpl::readers);

    private final WebClient hardcoverWebClient;

    private final HardcoverSeriesMapper mapper;

    public HardcoverSeriesClientImpl(
        final WebClient hardcoverWebClient,
        final HardcoverSeriesMapper mapper
    ) {
        this.hardcoverWebClient = hardcoverWebClient;
        this.mapper = mapper;
    }

    @Override
    public Optional<SourceSeries> fetchSeries(final String query) {
        return bestCandidate(query).flatMap(hit -> resolve(query, hit));
    }

    private Optional<SourceSeries> resolve(
        final String query,
        final SeriesSearchDocument hit
    ) {
        final Integer id = HardcoverGraphQl.parseId(hit.id());
        if (id == null) {
            return Optional.empty();
        }
        final SeriesEnumerationResponse.Series series = enumerate(query, id);
        return Optional.ofNullable(mapper.toSourceSeries(hit, series));
    }

    private Optional<SeriesSearchDocument> bestCandidate(final String query) {
        try {
            final TypesenseSearchResponse<SeriesSearchDocument> response =
                hardcoverWebClient.post()
                    .bodyValue(new GraphQlRequest(SEARCH_QUERY, Map.of("q", query)))
                    .retrieve()
                    .bodyToMono(SERIES_HITS)
                    .block();
            return TypesenseHits.documents(response).stream()
                .filter(document -> document.name() != null)
                .max(BY_READERS);
        } catch (WebClientResponseException exception) {
            HardcoverGraphQl.recoverOrThrow(LOG, exception, query);
            return Optional.empty();
        }
    }

    private SeriesEnumerationResponse.@Nullable Series enumerate(final String query, final int id) {
        try {
            final SeriesEnumerationResponse response = hardcoverWebClient.post()
                .bodyValue(new GraphQlRequest(ENUM_QUERY, Map.of("id", id)))
                .retrieve()
                .bodyToMono(SeriesEnumerationResponse.class)
                .block();
            return firstSeries(response);
        } catch (WebClientResponseException exception) {
            HardcoverGraphQl.recoverOrThrow(LOG, exception, query);
            return null;
        }
    }

    private static SeriesEnumerationResponse.@Nullable Series firstSeries(
        final @Nullable SeriesEnumerationResponse response
    ) {
        return Optional.ofNullable(response)
            .map(SeriesEnumerationResponse::data)
            .map(SeriesEnumerationResponse.Data::series)
            .filter(list -> !list.isEmpty())
            .map(list -> list.get(0))
            .orElse(null);
    }

    private static int readers(final SeriesSearchDocument document) {
        final Integer count = document.readersCount();
        return count == null ? 0 : count;
    }
}
