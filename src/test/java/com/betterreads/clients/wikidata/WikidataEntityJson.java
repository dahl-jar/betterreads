package com.betterreads.clients.wikidata;

import com.betterreads.clients.Fixtures;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// PMD.TooManyMethods: one fluent method per entity field the tests vary
@SuppressWarnings("PMD.TooManyMethods")
public final class WikidataEntityJson {

    public static final String FIXTURE_QID = "Q190192";

    static final String HERBERT_QID = "Q7934";

    static final String HERBERT_NAME = "Frank Herbert";

    static final String HERBERT_WIKI_URL = "https://en.wikipedia.org/wiki/Frank_Herbert";

    static final String HERBERT_IMAGE = "Frank Herbert 1984.jpg";

    static final String MOORE_QID = "Q205739";

    static final String MOORE_NAME = "Alan Moore";

    private static final String ENTITIES = "entities";

    private static final String LABELS = "labels";

    private static final String OPEN_LIBRARY_KEYS = "P648";

    private static final String AWARDS = "P166";

    private static final String VALUE = "value";

    private static final String CLAIMS = "claims";

    private static final String SITELINKS = "sitelinks";

    private static final String SERIES = "P179";

    private static final String QUALIFIERS = "qualifiers";

    private static final String DATA_VALUE = "datavalue";

    private final ObjectNode json = Fixtures.parse("""
        {
          "id": "Q190192",
          "labels": {"en": {"value": "Dune"}},
          "sitelinks": {"enwiki": {"title": "Dune (novel)"}},
          "claims": {
            "P31": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q7725634"}}}}],
            "P50": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q7934"}}}}],
            "P136": [
              {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q905770"}}}},
              {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q2630193"}}}},
              {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q944250"}}}},
              {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q24925"}}}},
              {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q21802675"}}}}
            ],
            "P166": [
              {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q266012"}}}},
              {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q255032"}}}},
              {"mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q27496509"}}}}
            ],
            "P179": [{
              "mainsnak": {"snaktype": "value", "datavalue": {"value": {"id": "Q6095696"}}},
              "qualifiers": {"P1545": [{"datavalue": {"value": "1"}}]}
            }],
            "P244": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": "no2006084758"}}}],
            "P577": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": {"time": "+1965-00-00T00:00:00Z"}}}}],
            "P648": [{"mainsnak": {"snaktype": "value", "datavalue": {"value": "OL893527W"}}}]
          }
        }
        """);

    private WikidataEntityJson() {
    }

    public static WikidataEntityJson entity() {
        return new WikidataEntityJson();
    }

    static WikidataEntityJson herbert() {
        return entity().withId(HERBERT_QID).withoutLabels().withSitelink(HERBERT_NAME, HERBERT_WIKI_URL)
            .withoutClaims().withImage(HERBERT_IMAGE);
    }

    static WikidataEntityJson moore() {
        return entity().withId(MOORE_QID).withLabel(MOORE_NAME).withoutClaims();
    }

    public WikidataEntityJson withId(final String qid) {
        json.put("id", qid);
        return this;
    }

    public WikidataEntityJson withoutId() {
        json.remove("id");
        return this;
    }

    public WikidataEntityJson withLabel(final String label) {
        json.putObject(LABELS).putObject("en").put(VALUE, label);
        return this;
    }

    public WikidataEntityJson withoutLabels() {
        json.putObject(LABELS);
        return this;
    }

    public WikidataEntityJson withSitelink(final String title, final String url) {
        json.putObject(SITELINKS).putObject("enwiki").put("title", title).put("url", url);
        return this;
    }

    public WikidataEntityJson withoutSitelinks() {
        json.putObject(SITELINKS);
        return this;
    }

    public WikidataEntityJson withoutClaims() {
        json.putObject(CLAIMS);
        return this;
    }

    public WikidataEntityJson withInstanceOf(final String qid) {
        return withItems("P31", qid);
    }

    public WikidataEntityJson withAuthors(final String... qids) {
        return withItems("P50", qids);
    }

    WikidataEntityJson withAwards(final String... qids) {
        return withItems(AWARDS, qids);
    }

    WikidataEntityJson withPreferredPublicationDate(final String time) {
        final ObjectNode claim = json.withObject(CLAIMS).withArray("P577").addObject().put("rank", "preferred");
        datavalue(claim).putObject(VALUE).put("time", time);
        return this;
    }

    WikidataEntityJson withSeriesOrdinal(final String ordinal) {
        seriesClaim().putObject(QUALIFIERS).putArray("P1545").addObject().putObject(DATA_VALUE).put(VALUE, ordinal);
        return this;
    }

    WikidataEntityJson withoutSeriesOrdinal() {
        seriesClaim().remove(QUALIFIERS);
        return this;
    }

    public WikidataEntityJson withOpenLibraryKeys(final String... keys) {
        return withStrings(OPEN_LIBRARY_KEYS, keys);
    }

    public WikidataEntityJson withImage(final String file) {
        return withStrings("P18", file);
    }

    public WikidataEntityJson withoutGenres() {
        return without("P136");
    }

    public WikidataEntityJson withoutAwards() {
        return without(AWARDS);
    }

    public WikidataEntityJson withoutSeries() {
        return without(SERIES);
    }

    public WikidataEntityJson withoutLccn() {
        return without("P244");
    }

    public String qid() {
        return json.path("id").asString("");
    }

    public ObjectNode node() {
        return json.deepCopy();
    }

    public String entityData() {
        final ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.putObject(ENTITIES).set(qid(), json);
        return data.toString();
    }

    private WikidataEntityJson withItems(final String property, final String... qids) {
        final ArrayNode values = json.withObject(CLAIMS).putArray(property);
        for (final String qid : qids) {
            datavalue(values.addObject()).putObject(VALUE).put("id", qid);
        }
        return this;
    }

    private WikidataEntityJson withStrings(final String property, final String... strings) {
        final ArrayNode values = json.withObject(CLAIMS).putArray(property);
        for (final String value : strings) {
            datavalue(values.addObject()).put(VALUE, value);
        }
        return this;
    }

    private WikidataEntityJson without(final String property) {
        json.withObject(CLAIMS).remove(property);
        return this;
    }

    private ObjectNode seriesClaim() {
        return (ObjectNode) json.withObject(CLAIMS).withArray(SERIES).get(0);
    }

    private static ObjectNode datavalue(final ObjectNode claim) {
        return claim.putObject("mainsnak").put("snaktype", VALUE).putObject(DATA_VALUE);
    }
}
