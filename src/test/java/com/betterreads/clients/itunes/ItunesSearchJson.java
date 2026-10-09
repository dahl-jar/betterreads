package com.betterreads.clients.itunes;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class ItunesSearchJson {

    private static final JsonMapper MAPPER = new JsonMapper();

    private static final String RESPONSE = """
        {
          "resultCount": 1,
          "results": [
            {
              "trackName": "Howling Dark",
              "description": "The second novel of the Sun Eater series follows Hadrian Marlowe into fire."
            }
          ]
        }
        """;

    private static final String RESULT_COUNT = "resultCount";

    private static final String RESULTS = "results";

    private static final String TRACK_NAME = "trackName";

    private static final String DESCRIPTION = "description";

    static final String ARTWORK = "https://is1-ssl.mzstatic.com/image/thumb/a/3000x3000bb.jpg";

    static final String THUMBNAIL = "https://is1-ssl.mzstatic.com/image/thumb/a/100x100bb.jpg";

    static final String AUDIOBOOK_THUMBNAIL = "https://is1-ssl.mzstatic.com/image/thumb/b/100x100bb.jpg";

    static final String STORE = "https://books.apple.com/us/book/the-way-of-kings/id1?uo=4";

    private static final String KIND = "kind";

    private static final String ARTWORK_URL_100 = "artworkUrl100";

    private static final String TRACK_VIEW_URL = "trackViewUrl";

    private final ObjectNode json = (ObjectNode) MAPPER.readTree(RESPONSE);

    private final ObjectNode template = ((ObjectNode) entries().get(0)).deepCopy();

    private ItunesSearchJson() {
    }

    static ItunesSearchJson search() {
        return new ItunesSearchJson();
    }

    ItunesSearchJson withoutResults() {
        json.put(RESULT_COUNT, 0).putArray(RESULTS);
        return this;
    }

    ItunesSearchJson withTrackName(final String trackName) {
        ((ObjectNode) entries().get(0)).put(TRACK_NAME, trackName);
        return this;
    }

    ItunesSearchJson withDescription(final String description) {
        ((ObjectNode) entries().get(0)).put(DESCRIPTION, description);
        return this;
    }

    ItunesSearchJson withBook(final String kind, final String artworkUrl, final String storeUrl) {
        book((ObjectNode) entries().get(0), kind, artworkUrl, storeUrl);
        return this;
    }

    ItunesSearchJson andBook(final String kind, final String artworkUrl, final String storeUrl) {
        entries().add(book(template.deepCopy(), kind, artworkUrl, storeUrl));
        json.put(RESULT_COUNT, entries().size());
        return this;
    }

    private static ObjectNode book(
        final ObjectNode entry, final String kind, final String artworkUrl, final String storeUrl
    ) {
        return entry.put(KIND, kind).put(ARTWORK_URL_100, artworkUrl).put(TRACK_VIEW_URL, storeUrl);
    }

    ItunesSearchJson withResult(final String trackName, final String description) {
        entries().add(template.deepCopy().put(TRACK_NAME, trackName).put(DESCRIPTION, description));
        json.put(RESULT_COUNT, entries().size());
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }

    private ArrayNode entries() {
        return json.withArray(RESULTS);
    }
}
