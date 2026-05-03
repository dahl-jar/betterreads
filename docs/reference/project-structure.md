# Project structure

## Spring Boot package layout

```text
com.betterreads
  BetterReadsApplication
  config/
    SecurityConfig
    WebClientConfig
    CacheConfig
    JacksonConfig
  common/
    dto/
    exception/
    mapper/
    util/
  auth/
    controller/
    service/
    dto/
    entity/
    repository/
  catalog/
    controller/
    service/
    dto/
      SearchResultDto
      BookDetailDto
      TrendingBookDto
    entity/
      Book
      Author
      BookSubject
    repository/
    mapper/
  reviews/
    controller/
    service/
    dto/
    entity/
      Review
    repository/
  collections/
    controller/
    service/
    dto/
    entity/
      UserBookCollection
      ReadingStatus
    repository/
  clubs/
    controller/
    service/
    dto/
    entity/
      BookClub
      BookClubMembership
      ClubPost
      ClubComment
    repository/
  feed/
    controller/
    service/
    dto/
    entity/
      ActivityEvent
      FeedItem
    repository/
  recommendations/
    controller/
    service/
    dto/
      RecommendationDto
    entity/
      UserBookInteraction
      UserRecommendation
      SimilarBook
    repository/
  search/
    controller/
    service/
    dto/
      SearchRequestDto
      SearchResponseDto
    repository/
  integration/
    openlibrary/
      client/
        OpenLibraryClient
      dto/
      mapper/
      service/
        OpenLibraryCatalogService
  jobs/
    TrendingSyncJob
    MetadataEnrichmentJob
    RecommendationRefreshJob
    SearchIndexRefreshJob
```

Rules for this structure:
- controllers should only depend on services and DTOs
- repositories stay inside their feature modules
- OpenLibrary code stays isolated under `integration/openlibrary`
- recommendation persistence stays in `recommendations`
- search-specific indexing logic stays in `search`
- background scheduled work stays in `jobs`

## ML / recommendation service
The ML and recommendation training pipeline lives in a separate `ml-api` repository. It connects to the same PostgreSQL database to read interaction data and write back computed recommendations.
