package com.betterreads.clients.loc;

import java.util.List;

import com.betterreads.clients.XmlDocuments;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

// PMD.TooManyMethods: fixture builder with one method per MODS field a test changes.
@SuppressWarnings("PMD.TooManyMethods")
final class LocRecords {

    private static final String NAME_PART = "namePart";

    private static final String RELATED_ITEM = "relatedItem";

    private static final String NAME = "name";

    private static final String MODS = "http://www.loc.gov/mods/v3";

    private static final String TYPE = "type";

    private static final String TITLE_INFO = "titleInfo";

    private static final String IDENTIFIER = "identifier";

    private static final String ISBN = "isbn";

    private static final String DATE_ISSUED = "dateIssued";

    private static final String ENCODING = "encoding";

    private static final String POINT = "point";

    static final String SRU = "http://www.loc.gov/zing/srw/";

    static final String DUNE_LCCN = "2019287107";

    static final String DUNE_ISBN = "9780593099322";

    private static final String TITLE = "title";

    private static final String DUNE = """
        <zs:searchRetrieveResponse xmlns:zs="http://www.loc.gov/zing/srw/"><zs:records><zs:record><zs:recordData>
        <mods xmlns="http://www.loc.gov/mods/v3" version="3.8">
        <titleInfo><title>Dune</title></titleInfo>
        <name type="personal" usage="primary"><namePart>Herbert, Frank,</namePart></name>
        <genre authority="lcgft">Science fiction.</genre>
        <genre authority="fast">Fiction.</genre>
        <language><languageTerm authority="iso639-2b" type="code">eng</languageTerm></language>
        <originInfo><dateIssued encoding="marc">2019</dateIssued></originInfo>
        <physicalDescription><extent>xxii, 658 pages : map ; 24 cm.</extent></physicalDescription>
        <abstract type="Summary">Follows the adventures of Paul Atreides, the son of a betrayed duke.</abstract>
        <relatedItem type="series"><titleInfo>
        <title>Dune chronicles</title><partNumber>bk. 1</partNumber></titleInfo></relatedItem>
        <identifier type="isbn">9780593099322</identifier>
        <identifier type="isbn">059309932X</identifier>
        <identifier type="lccn">2019287107</identifier>
        </mods></zs:recordData></zs:record></zs:records></zs:searchRetrieveResponse>
        """;

    private final Document document;

    private LocRecords(final Document document) {
        this.document = document;
    }

    static LocRecords sruResponse() {
        return new LocRecords(XmlDocuments.parse(DUNE));
    }

    LocRecords withTitle(final String title) {
        return withText(TITLE, title);
    }

    LocRecords withPrimaryNamePart(final String namePart) {
        return withText(NAME_PART, namePart);
    }

    LocRecords withExtent(final String extent) {
        return withText("extent", extent);
    }

    LocRecords withDateIssued(final String year, final @Nullable String encoding, final @Nullable String point) {
        final Element date = elements(DATE_ISSUED).getFirst();
        date.setTextContent(year);
        date.removeAttribute(ENCODING);
        date.removeAttribute(POINT);
        if (encoding != null) {
            date.setAttribute(ENCODING, encoding);
        }
        if (point != null) {
            date.setAttribute(POINT, point);
        }
        return this;
    }

    LocRecords withAbstract(final String type, final String text) {
        final Element summary = elements("abstract").getFirst();
        summary.setAttribute(TYPE, type);
        summary.setTextContent(text);
        return this;
    }

    LocRecords withLanguageTerm(final String type, final String text) {
        final Element term = elements("languageTerm").getFirst();
        term.setAttribute(TYPE, type);
        term.setTextContent(text);
        return this;
    }

    LocRecords withoutTitle() {
        final Element mods = record();
        elements(TITLE_INFO).stream()
            .filter(titleInfo -> mods.equals(titleInfo.getParentNode()))
            .forEach(mods::removeChild);
        return this;
    }

    LocRecords withoutRecords() {
        final Node records = document.getElementsByTagNameNS(SRU, "records").item(0);
        while (records.hasChildNodes()) {
            records.removeChild(records.getFirstChild());
        }
        return this;
    }

    LocRecords withoutSeries() {
        return without(RELATED_ITEM);
    }

    LocRecords withoutNames() {
        return without(NAME);
    }

    LocRecords withContributor(final String namePart, final @Nullable String role) {
        return withName(namePart, role, false);
    }

    LocRecords withPrimaryContributor(final String namePart, final String role) {
        return withName(namePart, role, true);
    }

    LocRecords withNonSort(final String article) {
        final Element titleInfo = elements(TITLE_INFO).getFirst();
        titleInfo.insertBefore(element("nonSort", article), titleInfo.getFirstChild());
        return this;
    }

    private LocRecords withName(final String namePart, final @Nullable String role, final boolean primary) {
        final Element name = element(NAME, null);
        name.setAttribute(TYPE, "personal");
        if (primary) {
            name.setAttribute("usage", "primary");
        }
        name.appendChild(element(NAME_PART, namePart));
        if (role != null) {
            final Element roleTerm = element("roleTerm", role);
            roleTerm.setAttribute(TYPE, "text");
            name.appendChild(element("role", null)).appendChild(roleTerm);
        }
        record().appendChild(name);
        return this;
    }

    LocRecords withSeries(final String title, final @Nullable String partNumber) {
        final Element series = element(RELATED_ITEM, null);
        series.setAttribute(TYPE, "series");
        final Element titleInfo = element(TITLE_INFO, null);
        titleInfo.appendChild(element(TITLE, title));
        if (partNumber != null) {
            titleInfo.appendChild(element("partNumber", partNumber));
        }
        record().appendChild(series).appendChild(titleInfo);
        return this;
    }

    LocRecords withIsbns(final String... isbns) {
        elements(IDENTIFIER).stream()
            .filter(identifier -> ISBN.equals(identifier.getAttribute(TYPE)))
            .forEach(identifier -> identifier.getParentNode().removeChild(identifier));
        for (final String isbn : isbns) {
            final Element identifier = element(IDENTIFIER, isbn);
            identifier.setAttribute(TYPE, ISBN);
            record().appendChild(identifier);
        }
        return this;
    }

    String xml() {
        return XmlDocuments.write(document);
    }

    private LocRecords withText(final String tag, final String text) {
        elements(tag).getFirst().setTextContent(text);
        return this;
    }

    private LocRecords without(final String tag) {
        elements(tag).forEach(element -> element.getParentNode().removeChild(element));
        return this;
    }

    private Element record() {
        return elements("mods").getFirst();
    }

    private Element element(final String tag, final @Nullable String text) {
        final Element element = document.createElementNS(MODS, tag);
        if (text != null) {
            element.setTextContent(text);
        }
        return element;
    }

    private List<Element> elements(final String tag) {
        return XmlDocuments.elements(document, MODS, tag);
    }
}
