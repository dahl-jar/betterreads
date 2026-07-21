# Catalog pipeline

Catalog records combine data from five catalog sources. Each integration maps its response to `SourceBook` before catalog code reads it.

## Sources

- **Google Books:** title, subtitle, description, cover, ISBN, publisher, page count, language, and publication year
- **OpenLibrary:** title, subtitle, description, cover, ISBN, authors, subjects, language, and first-publication year
- **Hardcover:** title, description, cover, ISBN, authors, page count, publication year, rating, and numbered series membership
- **Wikidata:** title, authors, publication year, awards, subjects, and series membership
- **Library of Congress:** title, description, ISBN, authors, subjects, page count, language, and publication year

Apple Books, OpenLibrary, and Hardcover provide additional description candidates. Wikipedia provides a description when the stored text and the other candidates are unusable.

## Field selection

Most fields use the first present value in a fixed source order. Descriptions use the highest `DescriptionQuality` score. Subjects combine values from all live sources.

| Field | Selection |
|---|---|
| Title | Google Books, OpenLibrary, Hardcover, Wikidata, Library of Congress |
| Subtitle | Google Books, OpenLibrary |
| Description | Highest score from Google Books, OpenLibrary, Hardcover, Library of Congress |
| Cover | Google Books, Hardcover, OpenLibrary |
| Publication year | Discovery seed, then OpenLibrary, Google Books, Wikidata, Library of Congress, Hardcover |
| Publisher | Google Books |
| Authors | OpenLibrary, Google Books, Wikidata, Hardcover, Library of Congress |
| Page count | Google Books, Hardcover, Library of Congress |
| Language | Google Books, OpenLibrary, Library of Congress |
| Rating | Hardcover |
| Series | First Hardcover or Wikidata value with a name and numeric position |
| Awards | Wikidata |
| ISBN | Google Books, OpenLibrary, Library of Congress, Hardcover |
| Subjects | Union of all live sources |

A discovery seed's publication year takes precedence because later title or ISBN lookups may resolve a reprint. On collection from staging, stored values follow the live source chains. Stored subjects are used when the live sources return none. A stored rating is retained when Hardcover has no value. Series membership requires a current Hardcover or Wikidata result.

## Flow

1. `SourceCollector` fetches Google Books and OpenLibrary in parallel, then fetches Hardcover, Library of Congress, and Wikidata in parallel. Failed sources are omitted from the merge.
2. `SourceMerger` selects fields, records their sources, and produces a `MergedBook`.
3. `DescriptionSelector` cleans and scores description candidates. A candidate replaces the current description when its score is higher.
4. `PendingBookService` writes the result to `pending_book` by deduplication key.
5. `PendingBookPromoter` writes the record to `book` when title, author, cover, description, publication year, and ISBN are present.

Scheduled jobs retry staged records, refresh promoted books, replace weak descriptions, and mirror missing covers.

## Covers

Books store the selected external cover URL and an optional MinIO object key. Public responses return `/api/v1/images/covers/{key}`. The image endpoint reads the processed JPEG from MinIO. On a cache miss, it fetches the external image, rejects blocked network targets and undecodable input, scales it to at most 800 pixels, re-encodes it as JPEG, and stores it in MinIO.
