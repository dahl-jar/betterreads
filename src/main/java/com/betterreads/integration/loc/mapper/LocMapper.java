package com.betterreads.integration.loc.mapper;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.CatalogGenres;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.loc.SruTree;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * Maps a Library of Congress SRU response into a {@link SourceBook}.
 *
 * <p>The MODS record is read as a parsed tree by {@link SruTree} rather than bound to DTO records,
 * which avoids wrestling the MODS default namespace and the {@code zs:} SRU wrapper into Jackson XML
 * bindings. Identifier and series extraction live in {@link ModsIdentifiers} and {@link ModsSeries}.
 */
@Component
public class LocMapper {

    private static final Logger LOG = LoggerFactory.getLogger(LocMapper.class);

    private static final XmlMapper XML = new XmlMapper();

    private static final int MAX_GENRES = 25;

    private static final String TYPE = "type";

    private static final Pattern PAGE_COUNT = Pattern.compile("(\\d+)\\s*(?:pages|p\\.)");
    private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*$");
    private static final Pattern TRAILING_PERIOD_AFTER_WORD = Pattern.compile("(?<=\\p{L}{2})\\.\\s*$");

    /**
     * Returns the first record in the SRU response as a {@link SourceBook}, or empty when the
     * response holds no record or the XML does not parse.
     */
    public Optional<SourceBook> toSourceBook(final String sruXml) {
        return firstMods(sruXml).map(LocMapper::mapRecord);
    }

    private static Optional<JsonNode> firstMods(final String sruXml) {
        try {
            return Optional.ofNullable(SruTree.firstByTag(XML.readTree(sruXml), "mods"));
        } catch (JacksonException exception) {
            LOG.debug("loc.parse SRU response did not parse as XML", exception);
            return Optional.empty();
        }
    }

    private static SourceBook mapRecord(final JsonNode mods) {
        final List<String> genres = genres(mods);
        return SourceBook.builder(BookFieldSource.LOC)
            .isbn13(ModsIdentifiers.isbn13(mods))
            .locLccn(ModsIdentifiers.firstOfType(mods, "lccn"))
            .title(SruTree.firstText(SruTree.firstByTag(mods, "titleInfo"), "title"))
            .description(summary(mods))
            .publicationYear(marcYear(mods).orElse(null))
            .pageCount(pageCount(mods).orElse(null))
            .language(languageCode(mods))
            .authors(SourceAuthor.ofNames(authorNames(mods)))
            .rawSubjects(genres.isEmpty() ? null : genres)
            .seriesName(ModsSeries.name(mods))
            .seriesPosition(ModsSeries.position(mods).orElse(null))
            .build();
    }

    private static @Nullable List<String> authorNames(final JsonNode mods) {
        return SruTree.elements(mods, "name")
            .filter(name -> "primary".equals(SruTree.attribute(name, "usage")))
            .map(LocMapper::displayNamePart)
            .filter(namePart -> namePart != null)
            .map(LocMapper::stripTrailingPunctuation)
            .findFirst()
            .map(List::of)
            .orElse(null);
    }

    private static @Nullable String displayNamePart(final JsonNode name) {
        return SruTree.elements(name, "namePart")
            .filter(part -> SruTree.attribute(part, TYPE) == null)
            .map(SruTree::text)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }

    private static Optional<Integer> marcYear(final JsonNode mods) {
        return SruTree.elements(mods, "originInfo")
            .flatMap(origin -> SruTree.elements(origin, "dateIssued"))
            .filter(date -> "marc".equals(SruTree.attribute(date, "encoding")))
            .filter(date -> !"end".equals(SruTree.attribute(date, "point")))
            .flatMap(date -> SruTree.intValue(SruTree.text(date)).stream())
            .findFirst();
    }

    private static Optional<Integer> pageCount(final JsonNode mods) {
        final String extent = SruTree.firstText(SruTree.firstByTag(mods, "physicalDescription"), "extent");
        if (extent == null || extent.startsWith("v.")) {
            return Optional.empty();
        }
        final Matcher matcher = PAGE_COUNT.matcher(extent);
        return matcher.find() ? SruTree.intValue(matcher.group(1)) : Optional.empty();
    }

    private static @Nullable String summary(final JsonNode mods) {
        return SruTree.elements(mods, "abstract")
            .filter(node -> "Summary".equals(SruTree.attribute(node, TYPE)))
            .map(SruTree::text)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }

    private static @Nullable String languageCode(final JsonNode mods) {
        return SruTree.elements(mods, "language")
            .flatMap(language -> SruTree.elements(language, "languageTerm"))
            .filter(term -> "code".equals(SruTree.attribute(term, TYPE)))
            .map(SruTree::text)
            .filter(value -> value != null)
            .findFirst()
            .orElse(null);
    }

    private static List<String> genres(final JsonNode mods) {
        final List<String> raw = SruTree.elements(mods, "genre")
            .map(SruTree::text)
            .filter(value -> value != null)
            .toList();
        return CatalogGenres.reduceToCanonical(raw, MAX_GENRES);
    }

    private static String stripTrailingPunctuation(final String name) {
        final String withoutComma = TRAILING_COMMA.matcher(name.trim()).replaceAll("");
        return TRAILING_PERIOD_AFTER_WORD.matcher(withoutComma).replaceAll("");
    }
}
