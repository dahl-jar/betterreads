package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.betterreads.book.VerifiedMetadata;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class CheckedBookTest {

    @Test
    void shouldListOnlyTheHostsOfUnreachablePages() {
        final JsonNode answer = MetadataJson.metadata()
            .withSource(MetadataJson.YEAR_FIELD, "https://isfdb.org/title/1")
            .node().path("books").get(0);
        final CheckedBook book = new CheckedBook(VerifiedMetadata.NONE, Map.of(
            MetadataJson.YEAR_FIELD, FieldOutcome.PAGE_UNREACHABLE,
            MetadataJson.TITLE_FIELD, FieldOutcome.ACCEPTED), answer);

        final List<String> hosts = book.unreachableHosts();

        assertThat(hosts).containsExactly("isfdb.org");
    }
}
