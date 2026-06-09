# Catalog pipeline

How a book gets into the catalog, where each field comes from, and the order each field is picked.

## Sources

Five outside sources, each with its own client under `integration/<vendor>/` and explicit timeouts. Each client turns the source's response into an internal `SourceBook` before the merge sees it.

- **Google Books** (`/volumes?q=`): title, subtitle, description, ISBN, language, page count, publisher. It ranks recent reprints first, so its year and publisher belong to that reprint.
- **OpenLibrary** (search + work): authors, first-publication year, cover id, subjects.
- **Hardcover** (GraphQL): rating and rating count, series name and position. The only source with a rating.
- **Wikidata** (entity): awards, and a backup for title, year, and authors.
- **Library of Congress** (SRU/MARC): a backup for title, year, page count, language, ISBN, and the main source for finding new releases.

Two more sources fill in the description and nothing else: **Wikipedia** (through the book's Wikidata `enwiki` article) and **Apple Books** (iTunes search). They run after the merge.

Which source to trust per field came from calling every live API against a fixed set of books picked to trigger the cases the code handles. Findings are in `.local/source-trust.md`.

## Field order

Each field has its own source list. The first source on the list with a value for that field wins; a source higher on the list but empty steps aside for a lower one that has it.

| Field | Order | Reason |
|---|---|---|
| Title | Google, OpenLibrary, Hardcover, Wikidata, LoC | Google's title is the cleanest. `TitleCleaner` removes edition tags before it is saved. |
| Subtitle | Google, OpenLibrary | Google has the series subtitle when there is one. |
| Description | Google, OpenLibrary, Hardcover, LoC | The highest-scoring description across the list wins (see Flow, step 3). |
| Cover | Google, Hardcover, OpenLibrary | Google's cover matches the edition. OpenLibrary's is the backup. |
| Year | OpenLibrary, Google, Wikidata, LoC, Hardcover | OpenLibrary has the first-publication year. Google has the reprint year. |
| Authors | OpenLibrary, Google, Wikidata, Hardcover, LoC | OpenLibrary lists co-creators that Google leaves off graphic novels. |
| Page count | Google, Hardcover, LoC | Google when it is not 0. It returns 0 on reprints, which the mapper drops. |
| Language | Google, OpenLibrary, LoC | Two-letter code, reliable from Google. |
| Rating | Hardcover | The only source with a reader rating and vote count to trust. |
| Series | Hardcover, Wikidata | A series is set only when a source gives both a name and a number. A name with no number means a companion or guide filed under the series. |
| Awards | Wikidata | The only source with awards as data. |
| ISBN | Google, OpenLibrary, LoC, Hardcover | Real when present. An empty ISBN is saved as null. |
| Subjects | all five | Subjects are combined from every source, so the genre list is as wide as the sources allow. |

The year has one exception. When a book is found through a discovery seed (a Hardcover series or author hit) that has a year, the seed's year wins, because the other sources look the book up by title or ISBN and can land on a reprint.

## Flow

A book enters from a search that found no local match, or from the daily new-release job. Either way it starts as a seed `SourceBook` from one source. The seed then goes through:

1. **Collect.** `SourceCollector` calls the other sources for the seed in two rounds: the fast ones (Google, OpenLibrary) first, the slower ones (Hardcover, LoC, Wikidata) second. Sources in a round are called at the same time. Both rounds always run. A source that fails or times out is left out for this book, and the merge uses the rest.
2. **Merge.** `SourceMerger` picks each field by its order and builds one `MergedBook`, keeping track of which source gave each field.
3. **Describe.** `DescriptionSelector` cleans and scores every description it has (the merged sources' plus Wikipedia and Apple Books) and keeps the highest score. Scoring throws out stubs, library boilerplate, and dumped edition lists, and prefers a sensible length.
4. **Stage.** The merged book is written to the `pending_book` table under a dedup key built from the first source id it has. Staging the same key again updates the row. Two stages of the same book at once end as one row.
5. **Promote.** A book moves into `book` only once it has a title, an author, a cover, a real description, a publication year, and an ISBN. The check looks at whether each field is present; a book missing any stays in `pending_book` until a later run fills it. The promote locks the row first, so a rating a reader added at the same time is kept.

Slow work runs outside the request. A sweep moves finished candidates from `pending_book` to `book`; a refresh job re-pulls stale data; two backfills walk the catalog to fill short descriptions and copy covers the live path missed.

## Covers

A stored book keeps the outside cover URL the merge chose. When the API hands a cover to a client, it gives its own image URL, and that endpoint sends the bytes from MinIO. The first time a cover is asked for and it is not in MinIO yet, the outside image is downloaded once, re-encoded to a size-capped JPEG, and saved to MinIO; later requests get the saved copy. Re-encoding through an image decoder drops anything it can't decode as an image and removes the original file's metadata. The download only follows http(s) links that point to public addresses, and checks each redirect the same way.
