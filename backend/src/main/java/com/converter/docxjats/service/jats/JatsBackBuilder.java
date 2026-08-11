package com.converter.docxjats.service.jats;

/**
 * Construye el bloque {@code <back>} del artículo JATS: por ahora, solo la
 * lista de referencias ({@code <ref-list>}) y, cuando existe, el bloque de
 * agradecimientos ({@code <ack>}).
 */
public class JatsBackBuilder {

    private final StringBuilder ackBlock = new StringBuilder();
    private final StringBuilder refList = new StringBuilder();
    private boolean started = false;
    private boolean closed = false;
    private int refCounter = 1;
    private boolean ackOpened = false;

    public void openReferences(String title) {
        if (started && !closed) {
            closeReferences();
        }
        refList.append("<ref-list>\n<title>").append(XmlUtils.escape(title)).append("</title>\n");
        started = true;
        closed = false;
    }

    public void appendAcknowledgement(String text) {
        if (text == null) return;
        String clean = text.trim();
        if (clean.isBlank()) return;
        if (!ackOpened) {
            ackBlock.append("<ack>\n<title>Agradecimientos</title>\n");
            ackOpened = true;
        }
        ackBlock.append("<p>").append(XmlUtils.escape(clean)).append("</p>\n");
    }

    public boolean isOpen() {
        return started && !closed;
    }

    public void appendReference(String text) {
        if (text == null || text.isBlank()) return;
        String cleanRef = normalizeReference(text);
        if (cleanRef.isBlank()) return;
        if (!started) {
            openReferences("Referencias bibliográficas");
        }
        refList.append(buildReferenceXmlBlock(refCounter++, cleanRef));
    }

    public void closeReferences() {
        if (started && !closed) {
            refList.append("</ref-list>\n");
            closed = true;
        }
    }

    public boolean hasContent() {
        return started || ackOpened;
    }

    /** Cierra cualquier ref-list pendiente y devuelve el XML final del <back> (o "" si no hubo referencias). */
    public String build() {
        closeReferences();
        if (!hasContent()) return "";

        StringBuilder back = new StringBuilder();
        back.append("<back>\n");
        if (ackOpened) {
            back.append(ackBlock).append("</ack>\n");
        }
        back.append(refList);
        back.append("</back>\n");
        return back.toString();
    }

    private String normalizeReference(String text) {
        String clean = text.replace("\u00A0", " ");
        clean = clean.replaceAll("^\\s*\\d+\\.\\s*", "");
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean;
    }

    private String buildReferenceXmlBlock(int num, String cleanRef) {
        boolean hasUrl = cleanRef.matches("(?i).*(https?://\\S+).*");
        boolean hasDoi = cleanRef.matches("(?i).*10\\.\\d{4,9}/\\S+.*");
        String publicationType = detectPublicationType(cleanRef, hasUrl, hasDoi);

        StringBuilder xml = new StringBuilder();
        xml.append("<ref id=\"ref").append(num).append("\">\n");
        xml.append("<mixed-citation>").append(XmlUtils.escape(cleanRef)).append("</mixed-citation>\n");
        xml.append("<element-citation publication-type=\"").append(publicationType).append("\">\n");

        String year = extractYear(cleanRef);
        if (!year.isBlank()) {
            xml.append("<year>").append(XmlUtils.escape(year)).append("</year>\n");
        }

        String source = extractSource(cleanRef);
        if (!source.isBlank()) {
            xml.append("<source>").append(XmlUtils.escape(source)).append("</source>\n");
        }

        if (hasDoi) {
            String doi = cleanRef.replaceAll("(?i).*?(10\\.\\d{4,9}/\\S+).*", "$1");
            xml.append("<pub-id pub-id-type=\"doi\">").append(XmlUtils.escape(doi)).append("</pub-id>\n");
        }

        if (hasUrl) {
            String url = cleanRef.replaceAll("(?i).*?(https?://\\S+).*", "$1");
            xml.append("<ext-link ext-link-type=\"uri\" xlink:href=\"")
               .append(XmlUtils.escape(url)).append("\">")
               .append(XmlUtils.escape(url)).append("</ext-link>\n");
        }

        xml.append("</element-citation>\n");
        xml.append("</ref>\n");
        return xml.toString();
    }

    private String detectPublicationType(String cleanRef, boolean hasUrl, boolean hasDoi) {
        String lower = cleanRef.toLowerCase();
        if (hasUrl && lower.contains("[internet]")) {
            return "webpage";
        }
        if (hasDoi || lower.contains("journal") || lower.contains("revista") || lower.contains("vol")) {
            return "journal";
        }
        return "book";
    }

    private String extractYear(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b((?:19|20)\\d{2})\\b").matcher(text);
        String lastYear = "";
        while (matcher.find()) {
            lastYear = matcher.group(1);
        }
        return lastYear;
    }

    private String extractSource(String cleanRef) {
        String[] parts = cleanRef.split("\\.\\s+", 2);
        if (parts.length < 2) {
            return "";
        }
        String remainder = parts[1].trim();
        if (remainder.isBlank()) {
            return "";
        }
        int yearIndex = remainder.indexOf(extractYear(remainder));
        if (yearIndex > 0) {
            remainder = remainder.substring(0, yearIndex).trim();
        }
        return remainder.replaceAll("[.;,]+$", "").trim();
    }
}
