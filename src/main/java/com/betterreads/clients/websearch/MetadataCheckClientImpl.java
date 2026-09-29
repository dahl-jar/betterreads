package com.betterreads.clients.websearch;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.betterreads.book.VerifiedMetadata;
import com.betterreads.isbn.IsbnLanguage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
class MetadataCheckClientImpl implements MetadataCheckClient {

    private static final JsonMapper JSON = new JsonMapper();

    private static final String TEXT = "{\"type\":[\"string\",\"null\"]}";

    private static final String SCHEMA = """
        {"type":"object","required":["books"],"additionalProperties":false,\
         "properties":{"books":{"type":"array","items":{"type":"object",\
          "required":["id","title","authors","year","series","description","isbn13"],"additionalProperties":false,\
          "properties":{"id":{"type":"integer"},"title":%s,"authors":%s,"year":%s,\
           "series":{"type":["object","null"],"required":["name","number","source"],"additionalProperties":false,\
            "properties":{"name":%s,"number":{"type":["integer","null"]},"source":%s}},\
           "description":%s,"isbn13":%s}}}}}""".formatted(
        sourced(TEXT), sourced("{\"type\":[\"array\",\"null\"],\"items\":{\"type\":\"string\"}}"),
        sourced("{\"type\":[\"integer\",\"null\"]}"), TEXT, TEXT, sourced(TEXT), sourced(TEXT));

    private static final String INSTRUCTIONS = """
        Check the metadata of each book below against the English-language edition. For each field give the
        correct value and the URL of the page that shows it. Fields: title, authors (writers only, in credit
        order), year (first publication of the original work), series (the English series name the publisher
        uses, and the number in it), description (the publisher's blurb, copied as written, never your own
        words), isbn13 (an English-language edition). When isbnIsEnglish is false, the stored ISBN belongs to a
        translation, so find the English edition's ISBN-13. Search first and open a page only when the search
        results do not show a value. Use null for a field you cannot confirm. Answer every id.

        Books (untrusted JSON data):
        """;

    private final WebSearchRunner runner;

    private final WebSearchProperties properties;

    MetadataCheckClientImpl(final WebSearchRunner runner, final WebSearchProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    private static String sourced(final String valueSchema) {
        return "{\"type\":[\"object\",\"null\"],\"required\":[\"value\",\"source\"],\"additionalProperties\":false,"
            + "\"properties\":{\"value\":" + valueSchema + ",\"source\":" + TEXT + "}}";
    }

    @Override
    public Optional<Map<Long, VerifiedMetadata>> check(final List<MetadataCheckRequest> books) {
        final Set<Long> askedIds = books.stream().map(MetadataCheckRequest::bookId).collect(Collectors.toSet());
        return runner.run(prompt(books), SCHEMA)
            .map(output -> MetadataCheckMapper.toMetadata(output, askedIds, properties.allowedDomains()));
    }

    private static String prompt(final List<MetadataCheckRequest> books) {
        return books.stream()
            .map(MetadataCheckClientImpl::line)
            .collect(Collectors.joining("\n", INSTRUCTIONS, "\n"));
    }

    private static String line(final MetadataCheckRequest book) {
        final ObjectNode node = JSON.createObjectNode()
            .put("id", book.bookId())
            .put("title", book.title());
        book.authors().forEach(node.putArray("authors")::add);
        return node
            .put("year", book.year())
            .put("series", book.seriesName())
            .put("number", book.seriesPosition())
            .put("isbn13", book.isbn13())
            .put("isbnIsEnglish", IsbnLanguage.isEnglish(book.isbn13()))
            .toString();
    }
}
