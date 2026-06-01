package com.betterreads.integration.loc.discovery;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.betterreads.integration.loc.LocSru;
import com.betterreads.integration.loc.SruTree;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Walks the LoC SRU endpoint for newly-cataloged books in a year and subject bucket.
 *
 * <p>The walk requests {@code recordSchema=marcxml} because the MARC 008 cataloging date the recency
 * filter needs is absent from MODS. Each record is parsed independently, and an incomplete record is
 * skipped so one bad record cannot fail the bucket.
 */
@Component
public class LocDiscoveryClientImpl implements LocDiscoveryClient {

    private static final Logger LOG = LoggerFactory.getLogger(LocDiscoveryClientImpl.class);

    private static final XmlMapper XML = new XmlMapper();

    private static final String MARCXML_SCHEMA = "marcxml";
    private static final int PAGE_SIZE = 100;
    private static final int OFFSET_CEILING = 9999;

    private static final DateTimeFormatter MARC_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int MARC_DATE_LENGTH = 6;
    private static final String TAG = "tag";
    private static final String RECORDS = "records";
    private static final String RECORD = "record";
    private static final Pattern ISBN_13 = Pattern.compile("97[89]\\d{10}");

    private final LocSru sru;

    public LocDiscoveryClientImpl(final LocSru sru) {
        this.sru = sru;
    }

    @Override
    public List<LocDiscoveryRecord> discoverByDate(
        final int year,
        final String lcshSubject,
        final LocalDate catalogedSince
    ) {
        final String cql = "dc.date=" + year + " and bath.subject=\"" + lcshSubject + "\"";
        final List<LocDiscoveryRecord> found = new ArrayList<>();
        int startRecord = 1;
        int total = Integer.MAX_VALUE;
        while (startRecord <= Math.min(total, OFFSET_CEILING)) {
            final JsonNode root = sru.searchRetrieve(cql, MARCXML_SCHEMA, startRecord, PAGE_SIZE)
                .map(this::readTree)
                .orElse(null);
            if (root == null) {
                break;
            }
            total = totalRecords(root);
            found.addAll(parsePage(root, catalogedSince));
            final int pageCount = recordCount(root);
            if (pageCount == 0) {
                break;
            }
            startRecord += pageCount;
        }
        return List.copyOf(found);
    }

    private static int totalRecords(final JsonNode root) {
        return SruTree.intValue(SruTree.firstText(root, "numberOfRecords")).orElse(0);
    }

    private static int recordCount(final JsonNode root) {
        final JsonNode records = SruTree.firstByTag(root, RECORDS);
        return records == null ? 0 : (int) SruTree.elements(records, RECORD).count();
    }

    private static List<LocDiscoveryRecord> parsePage(final JsonNode root, final LocalDate catalogedSince) {
        final JsonNode records = SruTree.firstByTag(root, RECORDS);
        if (records == null) {
            return List.of();
        }
        return SruTree.elements(records, RECORD)
            .map(LocDiscoveryClientImpl::toRecord)
            .flatMap(Optional::stream)
            .filter(record -> !record.catalogedOn().isBefore(catalogedSince))
            .toList();
    }

    private static Optional<LocDiscoveryRecord> toRecord(final JsonNode sruRecord) {
        final String lccn = subfieldA(sruRecord, "010");
        final LocalDate catalogedOn = catalogingDate(sruRecord);
        final String title = trimTitle(subfieldA(sruRecord, "245"));
        if (lccn == null || catalogedOn == null || title == null) {
            return Optional.empty();
        }
        return Optional.of(new LocDiscoveryRecord(lccn.trim(), isbn13(sruRecord), title, catalogedOn));
    }

    private static @Nullable LocalDate catalogingDate(final JsonNode sruRecord) {
        final String controlField008 = SruTree.descendants(sruRecord, "controlfield")
            .filter(node -> "008".equals(SruTree.attribute(node, TAG)))
            .map(SruTree::text)
            .filter(value -> value != null && value.length() >= MARC_DATE_LENGTH)
            .findFirst()
            .orElse(null);
        if (controlField008 == null) {
            return null;
        }
        try {
            return LocalDate.parse(controlField008.substring(0, MARC_DATE_LENGTH), MARC_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static @Nullable String subfieldA(final JsonNode sruRecord, final String tag) {
        return subfieldsA(sruRecord, tag).findFirst().orElse(null);
    }

    private static @Nullable String isbn13(final JsonNode sruRecord) {
        return subfieldsA(sruRecord, "020")
            .map(value -> value.replaceAll("\\D", ""))
            .filter(value -> ISBN_13.matcher(value).matches())
            .findFirst()
            .orElse(null);
    }

    private static Stream<String> subfieldsA(final JsonNode sruRecord, final String tag) {
        return SruTree.descendants(sruRecord, "datafield")
            .filter(field -> tag.equals(SruTree.attribute(field, TAG)))
            .flatMap(field -> SruTree.elements(field, "subfield"))
            .filter(subfield -> "a".equals(SruTree.attribute(subfield, "code")))
            .map(SruTree::text)
            .filter(value -> value != null);
    }

    private static @Nullable String trimTitle(final @Nullable String title) {
        if (title == null) {
            return null;
        }
        final String trimmed = title.replaceAll("\\s*[/:]\\s*$", "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private @Nullable JsonNode readTree(final String body) {
        try {
            return XML.readTree(body);
        } catch (JacksonException exception) {
            LOG.warn("loc.discovery response did not parse as SRU XML", exception);
            return null;
        }
    }
}
