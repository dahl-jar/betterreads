package com.betterreads.clients.loc;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CatalogGenres;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class LocMapper {

    private static final Pattern PAGE_COUNT = Pattern.compile("(\\d+)\\s*(?:pages|p\\.)");

    Optional<SourceBook> toSourceBook(final String sruXml) {
        return LocSruTree.parse(sruXml)
            .map(root -> LocSruTree.firstByTag(root, "mods"))
            .map(LocMapper::mapRecord);
    }

    private static SourceBook mapRecord(final JsonNode mods) {
        final List<String> genres = genres(mods);
        final List<String> authors = ModsNames.authorNames(mods);
        return SourceBook.builder(BookFieldSource.LOC)
            .isbn13(ModsIdentifiers.isbn13(mods))
            .locLccn(ModsIdentifiers.firstOfType(mods, "lccn"))
            .title(title(mods))
            .description(summary(mods))
            .publicationYear(marcYear(mods).orElse(null))
            .pageCount(pageCount(mods).orElse(null))
            .language(languageCode(mods))
            .authors(SourceAuthor.ofNames(authors.isEmpty() ? null : authors))
            .rawSubjects(genres.isEmpty() ? null : genres)
            .seriesName(ModsSeries.name(mods))
            .seriesPosition(ModsSeries.position(mods).orElse(null))
            .build();
    }

    private static @Nullable String title(final JsonNode mods) {
        final JsonNode titleInfo = LocSruTree.elements(mods, "titleInfo").findFirst().orElse(null);
        final String title = LocSruTree.firstText(titleInfo, "title");
        if (title == null) {
            return null;
        }
        final String nonSort = LocSruTree.firstText(titleInfo, "nonSort");
        if (nonSort == null || nonSort.isBlank()) {
            return title;
        }
        final String article = nonSort.strip();
        return article.endsWith("'") ? article + title : article + " " + title;
    }

    private static Optional<Integer> marcYear(final JsonNode mods) {
        return LocSruTree.elements(mods, "originInfo")
            .flatMap(origin -> LocSruTree.elements(origin, "dateIssued"))
            .filter(date -> "marc".equals(LocSruTree.attribute(date, "encoding")))
            .filter(date -> !"end".equals(LocSruTree.attribute(date, "point")))
            .flatMap(date -> LocSruTree.intValue(LocSruTree.text(date)).stream())
            .findFirst();
    }

    private static Optional<Integer> pageCount(final JsonNode mods) {
        final String extent = LocSruTree.firstText(LocSruTree.firstByTag(mods, "physicalDescription"), "extent");
        if (extent == null || extent.startsWith("v.")) {
            return Optional.empty();
        }
        final Matcher matcher = PAGE_COUNT.matcher(extent);
        return matcher.find() ? LocSruTree.intValue(matcher.group(1)) : Optional.empty();
    }

    private static @Nullable String summary(final JsonNode mods) {
        return LocSruTree.elements(mods, "abstract")
            .filter(LocSruTree.hasType("Summary"))
            .flatMap(LocSruTree::textOf)
            .findFirst()
            .orElse(null);
    }

    private static @Nullable String languageCode(final JsonNode mods) {
        return LocSruTree.elements(mods, "language")
            .flatMap(language -> LocSruTree.elements(language, "languageTerm"))
            .filter(LocSruTree.hasType("code"))
            .flatMap(LocSruTree::textOf)
            .findFirst()
            .orElse(null);
    }

    private static List<String> genres(final JsonNode mods) {
        return CatalogGenres.reduceToCanonical(LocSruTree.elements(mods, "genre")
            .flatMap(LocSruTree::textOf)
            .toList());
    }
}
