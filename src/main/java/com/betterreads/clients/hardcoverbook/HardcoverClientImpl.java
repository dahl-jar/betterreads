package com.betterreads.clients.hardcoverbook;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.HardcoverGraphQlRequest;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverBookNodeMapper;
import com.betterreads.clients.hardcover.HardcoverGraphQl;
import com.betterreads.clients.hardcover.TypesenseSearchResponse;
import com.betterreads.text.TextMatch;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
class HardcoverClientImpl implements HardcoverClient {

    private static final Logger LOG = LoggerFactory.getLogger(HardcoverClientImpl.class);

    private static final String SEARCH_QUERY = """
        query Search($q: String!) {
          search(query: $q, query_type: "Book", per_page: 5, page: 1) { results }
        }
        """;

    private static final String BOOK_BY_ID_QUERY = """
        query BookById($id: Int!) {
          books(where: {id: {_eq: $id}}) {
        """ + HardcoverBookNode.FIELDS + """
          }
        }
        """;

    private static final ParameterizedTypeReference<TypesenseSearchResponse<HardcoverDocument>>
        BOOK_HITS = new ParameterizedTypeReference<>() { };

    private static final ParameterizedTypeReference<BookByIdResponse> BOOK_BY_ID =
        new ParameterizedTypeReference<>() { };

    private static final Comparator<HardcoverDocument> BY_READ_COUNT =
        Comparator.comparingInt(HardcoverClientImpl::readCount);

    private final WebClient hardcoverWebClient;

    private final HardcoverMapper mapper;

    public HardcoverClientImpl(final WebClient hardcoverWebClient, final HardcoverMapper mapper) {
        this.hardcoverWebClient = hardcoverWebClient;
        this.mapper = mapper;
    }

    @Override
    public BookFieldSource source() {
        return BookFieldSource.HARDCOVER;
    }

    @Override
    public Optional<SourceBook> fetchByIsbn(final String isbn) {
        return searchAndMap(isbn, document -> true);
    }

    @Override
    public Optional<SourceBook> fetchByTitleAuthor(final String title, final String author) {
        return searchAndMap(title,
            document -> titleMatches(document, title) && authorMatches(document, author));
    }

    @Override
    public Optional<SourceBook> fetchByHardcoverId(final String hardcoverId) {
        return HardcoverGraphQl.parseId(hardcoverId)
            .flatMap(this::bookById)
            .flatMap(HardcoverBookNodeMapper::toSourceBookWithSeries);
    }

    private Optional<HardcoverBookNode> bookById(final Integer id) {
        return HardcoverGraphQl.post(hardcoverWebClient, LOG,
                new HardcoverGraphQlRequest(BOOK_BY_ID_QUERY, Map.of("id", id)), BOOK_BY_ID,
                String.valueOf(id))
            .flatMap(BookByIdResponse::firstBook);
    }

    private Optional<SourceBook> searchAndMap(
        final String query,
        final Predicate<HardcoverDocument> accept
    ) {
        return search(query).filter(accept).map(this::toSourceBook);
    }

    private @Nullable SourceBook toSourceBook(final HardcoverDocument document) {
        final SourceBook book = mapper.toSourceBook(document);
        if (book == null || !mapper.hasIssueRunSeries(document)) {
            return book;
        }
        return HardcoverGraphQl.parseId(document.id())
            .flatMap(this::bookById)
            .map(node -> mapper.withSeriesOf(book, node))
            .orElse(book);
    }

    private Optional<HardcoverDocument> search(final String query) {
        return HardcoverGraphQl.search(hardcoverWebClient, LOG, SEARCH_QUERY, query, BOOK_HITS).stream()
            .filter(document -> document.title() != null)
            .max(BY_READ_COUNT);
    }

    private static int readCount(final HardcoverDocument document) {
        return Objects.requireNonNullElseGet(document.usersReadCount(),
            () -> Objects.requireNonNullElse(document.ratingsCount(), 0));
    }

    private static boolean titleMatches(final HardcoverDocument document, final String query) {
        final String title = Objects.requireNonNull(document.title(), "search drops hits without a title");
        return TextMatch.eitherContainsIgnoreCase(title.trim(), query.trim());
    }

    private static boolean authorMatches(final HardcoverDocument document, final String author) {
        final List<String> names = document.authorNames();
        if (names == null) {
            return false;
        }
        final String trimmedAuthor = author.trim();
        return names.stream().anyMatch(name -> name != null
            && TextMatch.eitherContainsIgnoreCase(name, trimmedAuthor));
    }
}
