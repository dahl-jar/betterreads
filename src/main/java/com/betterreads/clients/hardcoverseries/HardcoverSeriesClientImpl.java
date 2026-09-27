package com.betterreads.clients.hardcoverseries;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.betterreads.booksource.SourceSeries;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverGraphQlRequest;
import com.betterreads.clients.hardcover.HardcoverGraphQl;
import com.betterreads.clients.hardcover.TypesenseSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * A Series search ranks by relevance, so a parody or fan series can outrank the real one and the hit
 * with the most readers wins.
 */
@Component
class HardcoverSeriesClientImpl implements HardcoverSeriesClient {

    private static final Logger LOG = LoggerFactory.getLogger(HardcoverSeriesClientImpl.class);

    private static final String SEARCH_QUERY = """
        query SeriesSearch($q: String!) {
          search(query: $q, query_type: "Series", per_page: 5, page: 1) { results }
        }
        """;

    private static final String ENUM_QUERY = """
        query SeriesEnum($id: Int!) {
          series(where: {id: {_eq: $id}}) {
            primary_books_count
            book_series(order_by: {position: asc}) {
              position
              book {
        """ + HardcoverBookNode.FIELDS + """
              }
            }
          }
        }
        """;

    private static final ParameterizedTypeReference<TypesenseSearchResponse<SeriesSearchDocument>>
        SERIES_HITS = new ParameterizedTypeReference<>() { };

    private static final ParameterizedTypeReference<SeriesEnumerationResponse> ENUMERATION =
        new ParameterizedTypeReference<>() { };

    private static final Comparator<SeriesSearchDocument> BY_READERS =
        Comparator.comparingInt(document -> Objects.requireNonNullElse(document.readersCount(), 0));

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
        return HardcoverGraphQl.parseId(hit.id())
            .flatMap(id -> enumerate(query, id))
            .map(series -> mapper.toSourceSeries(hit, series));
    }

    private Optional<SeriesSearchDocument> bestCandidate(final String query) {
        return HardcoverGraphQl.search(hardcoverWebClient, LOG, SEARCH_QUERY, query, SERIES_HITS).stream()
            .filter(document -> document.name() != null)
            .max(BY_READERS);
    }

    private Optional<SeriesEnumerationResponse.Series> enumerate(final String query, final int id) {
        return HardcoverGraphQl.post(hardcoverWebClient, LOG,
                new HardcoverGraphQlRequest(ENUM_QUERY, Map.of("id", id)), ENUMERATION, query)
            .map(SeriesEnumerationResponse::data)
            .map(SeriesEnumerationResponse.Data::series)
            .flatMap(series -> series.stream().findFirst());
    }
}
