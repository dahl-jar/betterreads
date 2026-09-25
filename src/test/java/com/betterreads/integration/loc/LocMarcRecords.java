package com.betterreads.integration.loc;

import java.util.List;

import com.betterreads.integration.XmlDocuments;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class LocMarcRecords {

    private static final String MARC = "http://www.loc.gov/MARC21/slim";

    private static final String TAG = "tag";

    private static final String RECORD = "record";

    private final Document document;

    private final Element template;

    private LocMarcRecords(final Document document) {
        this.document = document;
        this.template = (Element) sruElements(RECORD).getFirst().cloneNode(true);
    }

    public static LocMarcRecords marcResponse() {
        return new LocMarcRecords(XmlDocuments.fixture("loc/sru-marcxml-response.xml"));
    }

    public LocMarcRecords withRecord(final String lccn, final String title) {
        final Element record = (Element) template.cloneNode(true);
        subfieldA(record, "010").setTextContent("  " + lccn);
        subfieldA(record, "245").setTextContent(title + " /");
        sruElements("records").getFirst().appendChild(record);
        return withTotal(sruElements(RECORD).size());
    }

    public LocMarcRecords withRecord(final String lccn, final String title, final @Nullable String field008) {
        withRecord(lccn, title);
        final Element control = field(sruElements(RECORD).getLast(), "controlfield", "008");
        if (field008 == null) {
            control.getParentNode().removeChild(control);
        } else {
            control.setTextContent(field008);
        }
        return this;
    }

    public LocMarcRecords withoutRecords() {
        sruElements(RECORD).forEach(record -> record.getParentNode().removeChild(record));
        return withTotal(0);
    }

    public LocMarcRecords withTotal(final int total) {
        sruElements("numberOfRecords").getFirst().setTextContent(Integer.toString(total));
        return this;
    }

    public String xml() {
        return XmlDocuments.write(document);
    }

    private List<Element> sruElements(final String tag) {
        return XmlDocuments.elements(document, LocRecords.SRU, tag);
    }

    private static Element subfieldA(final Element record, final String tag) {
        return XmlDocuments.elements(field(record, "datafield", tag), MARC, "subfield").getFirst();
    }

    private static Element field(final Element record, final String kind, final String tag) {
        return XmlDocuments.elements(record, MARC, kind).stream()
            .filter(field -> tag.equals(field.getAttribute(TAG)))
            .findFirst()
            .orElseThrow();
    }
}
