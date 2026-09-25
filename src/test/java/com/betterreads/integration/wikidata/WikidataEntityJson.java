package com.betterreads.integration.wikidata;

import com.betterreads.integration.Fixtures;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@SuppressWarnings("PMD.TooManyMethods")
public final class WikidataEntityJson {

    public static final String FIXTURE_QID = "Q190192";

    private static final String ENTITIES = "entities";

    private static final String LABELS = "labels";

    private static final String OPEN_LIBRARY_KEYS = "P648";

    private static final String VALUE = "value";

    private static final String CLAIMS = "claims";

    private static final String SITELINKS = "sitelinks";

    private final ObjectNode json =
        (ObjectNode) Fixtures.json("wikidata/entity-data.json").path(ENTITIES).path(FIXTURE_QID);

    private WikidataEntityJson() {
    }

    public static WikidataEntityJson entity() {
        return new WikidataEntityJson();
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
        return without("P166");
    }

    public WikidataEntityJson withoutSeries() {
        return without("P179");
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
            datavalue(values).putObject(VALUE).put("id", qid);
        }
        return this;
    }

    private WikidataEntityJson withStrings(final String property, final String... strings) {
        final ArrayNode values = json.withObject(CLAIMS).putArray(property);
        for (final String value : strings) {
            datavalue(values).put(VALUE, value);
        }
        return this;
    }

    private WikidataEntityJson without(final String property) {
        json.withObject(CLAIMS).remove(property);
        return this;
    }

    private static ObjectNode datavalue(final ArrayNode values) {
        return values.addObject().putObject("mainsnak").put("snaktype", VALUE).putObject("datavalue");
    }
}
