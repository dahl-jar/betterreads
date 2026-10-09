package com.betterreads.clients.hardcoverbook;

import com.betterreads.booksource.NullableLists;
import com.betterreads.clients.hardcover.HardcoverBookNode;
import com.betterreads.clients.hardcover.HardcoverBookNodeMapper;
import com.betterreads.isbn.Isbn13;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record HardcoverDocument(
    @Nullable String id,
    @Nullable String title,
    @Nullable String description,
    @Nullable Integer releaseYear,
    @Nullable Integer pages,
    @Nullable Double rating,
    @Nullable Integer ratingsCount,
    @Nullable Integer usersReadCount,
    @Nullable List<String> authorNames,
    @Nullable List<String> isbns,
    @Nullable List<String> genres,
    HardcoverBookNode.@Nullable Image image,
    @Nullable FeaturedSeries featuredSeries,
    @Nullable List<HardcoverBookNode.Contribution> contributions,
    @Nullable Boolean compilation,
    @Nullable Integer bookCategoryId
) {

    public HardcoverDocument {
        authorNames = NullableLists.copyOf(authorNames);
        isbns = NullableLists.copyOf(isbns);
        genres = NullableLists.copyOf(genres);
        contributions = NullableLists.copyOf(contributions);
    }

    @Override
    @Nullable
    public List<HardcoverBookNode.Contribution> contributions() {
        return NullableLists.copyOf(contributions);
    }

    @Override
    @Nullable
    public List<String> authorNames() {
        return NullableLists.copyOf(authorNames);
    }

    @Override
    @Nullable
    public List<String> isbns() {
        return NullableLists.copyOf(isbns);
    }

    @Override
    @Nullable
    public List<String> genres() {
        return NullableLists.copyOf(genres);
    }

    public @Nullable FeaturedSeries storySeries() {
        return HardcoverBookNodeMapper.isProseCompilation(compilation, bookCategoryId) ? null : featuredSeries;
    }

    public @Nullable String firstIsbn13() {
        return isbns == null ? null : isbns.stream().filter(Isbn13::isValid).findFirst().orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeaturedSeries(@Nullable Double position, @Nullable Series series) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Series(@Nullable String name) { }
    }
}
