package com.converter.docxjats.service.jats;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construye el bloque {@code <back>} del artículo JATS con alta granularidad,
 * parseando cada línea de referencia bibliográfica mediante expresiones regulares
 * y heurísticas para generar tanto {@code <mixed-citation>} como {@code <element-citation>}
 * estructurados (autores, título, fuente/revista, año, volumen, número, páginas, DOI, URL).
 */
public class JatsBackBuilder {

    private static final Pattern REFERENCE_NUMBER = Pattern.compile("^\\s*(?:\\[(\\d+)\\]|(\\d+))[.)]?\\s*(.*)$");
    private static final Pattern DOI_PATTERN = Pattern.compile("(?i)\\b(10\\.\\d{4,9}/[-._;()/:a-z0-9]+)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    
    private static final Pattern JOURNAL_PATTERN = Pattern.compile(
        "^(.*?)\\.\\s+(.*?)\\.\\s+([^.]+?)\\.\\s+(\\d{4});(\\d+)(?:\\((\\d+)\\))?:(\\d+)(?:-(\\d+))?.*$",
        Pattern.DOTALL
    );

    private final StringBuilder refList = new StringBuilder();
    private boolean started = false;
    private boolean closed = false;
    private int refCounter = 1;

    public void openReferences(String title) {
        if (started && !closed) {
            closeReferences();
        }
        refList.append("<ref-list>\n<title>")
               .append(XmlUtils.escape(title != null ? title : "Referencias bibliográficas"))
               .append("</title>\n");
        started = true;
        closed = false;
    }

    public boolean isOpen() {
        return started && !closed;
    }

    public void appendReference(String text) {
        if (text == null || text.isBlank()) return;

        if (!started) {
            openReferences("Referencias bibliográficas");
        }

        Matcher numbered = REFERENCE_NUMBER.matcher(text);
        String referenceId = numbered.matches() && numbered.group(1) != null
                ? numbered.group(1)
                : numbered.matches() && numbered.group(2) != null ? numbered.group(2) : String.valueOf(refCounter);
        String citationText = numbered.matches() ? numbered.group(3) : text.trim();

        Matcher doiMatcher = DOI_PATTERN.matcher(citationText);
        String doi = doiMatcher.find() ? doiMatcher.group(1) : null;

        Matcher urlMatcher = URL_PATTERN.matcher(citationText);
        String url = urlMatcher.find() ? urlMatcher.group() : null;

        Matcher yearMatcher = YEAR_PATTERN.matcher(citationText);
        String year = yearMatcher.find() ? yearMatcher.group(1) : null;

        refList.append("<ref id=\"B").append(referenceId).append("\">\n");
        refList.append("<label>").append(referenceId).append("</label>\n");

        // Mixed Citation
        refList.append("<mixed-citation>").append(XmlUtils.escape(citationText));
        if (doi != null) {
            refList.append(" doi: ").append(XmlUtils.escape(doi));
        }
        refList.append("</mixed-citation>\n");

        // Structured Element Citation
        Matcher journalMatcher = JOURNAL_PATTERN.matcher(citationText);
        if (journalMatcher.matches()) {
            String authorsStr = journalMatcher.group(1);
            String articleTitle = journalMatcher.group(2);
            String source = journalMatcher.group(3);
            String yr = journalMatcher.group(4);
            String volume = journalMatcher.group(5);
            String issue = journalMatcher.group(6);
            String fpage = journalMatcher.group(7);
            String lpage = journalMatcher.group(8);

            refList.append("<element-citation publication-type=\"journal\">\n");
            parseAndAppendPersonGroup(refList, authorsStr);
            if (articleTitle != null && !articleTitle.isBlank()) {
                refList.append("<article-title>").append(XmlUtils.escape(articleTitle.trim())).append("</article-title>\n");
            }
            if (source != null && !source.isBlank()) {
                refList.append("<source>").append(XmlUtils.escape(source.trim())).append("</source>\n");
            }
            if (yr != null) {
                refList.append("<year>").append(yr).append("</year>\n");
            }
            if (volume != null) {
                refList.append("<volume>").append(volume).append("</volume>\n");
            }
            if (issue != null) {
                refList.append("<issue>").append(issue).append("</issue>\n");
            }
            if (fpage != null) {
                refList.append("<fpage>").append(fpage).append("</fpage>\n");
            }
            if (lpage != null) {
                refList.append("<lpage>").append(lpage).append("</lpage>\n");
            }
            if (doi != null) {
                refList.append("<pub-id pub-id-type=\"doi\">").append(XmlUtils.escape(doi)).append("</pub-id>\n");
            }
            refList.append("</element-citation>\n");

        } else if (citationText.toLowerCase().contains("[internet]") || url != null) {
            int firstDot = citationText.indexOf('.');
            String authorPart = firstDot > 0 ? citationText.substring(0, firstDot).trim() : citationText;

            refList.append("<element-citation publication-type=\"webpage\">\n");
            parseAndAppendPersonGroup(refList, authorPart);
            refList.append("<source>").append(XmlUtils.escape(citationText)).append("</source>\n");
            if (year != null) {
                refList.append("<year>").append(year).append("</year>\n");
            }
            if (url != null) {
                refList.append("<comment>Disponible en: <ext-link ext-link-type=\"uri\" xlink:href=\"")
                       .append(XmlUtils.escape(url)).append("\">")
                       .append(XmlUtils.escape(url)).append("</ext-link></comment>\n");
            }
            refList.append("</element-citation>\n");

        } else {
            refList.append("<element-citation publication-type=\"book\">\n");
            int firstDot = citationText.indexOf('.');
            String authorPart = firstDot > 0 ? citationText.substring(0, firstDot).trim() : citationText;
            parseAndAppendPersonGroup(refList, authorPart);
            refList.append("<source>").append(XmlUtils.escape(citationText)).append("</source>\n");
            if (year != null) {
                refList.append("<year>").append(year).append("</year>\n");
            }
            if (doi != null) {
                refList.append("<pub-id pub-id-type=\"doi\">").append(XmlUtils.escape(doi)).append("</pub-id>\n");
            }
            refList.append("</element-citation>\n");
        }

        refList.append("</ref>\n");
        refCounter++;
    }

    private void parseAndAppendPersonGroup(StringBuilder sb, String authorsStr) {
        if (authorsStr == null || authorsStr.isBlank()) return;
        sb.append("<person-group person-group-type=\"author\">\n");

        if (!authorsStr.contains(",") && !authorsStr.contains(" ") && authorsStr.length() < 30 ||
            authorsStr.toLowerCase().contains("organization") || authorsStr.toLowerCase().contains("organización") ||
            authorsStr.toLowerCase().contains("ministerio") || authorsStr.toLowerCase().contains("march of dimes")) {
            sb.append("<collab>").append(XmlUtils.escape(authorsStr.trim())).append("</collab>\n");
        } else {
            String[] authors = authorsStr.split(",\\s*(?:and|y)?\\s+|\\sand\\s+|\\sy\\s+");
            for (String auth : authors) {
                auth = auth.trim();
                if (auth.equalsIgnoreCase("et al.") || auth.equalsIgnoreCase("et al")) {
                    sb.append("<etal/>\n");
                    continue;
                }
                int spaceIdx = auth.lastIndexOf(' ');
                if (spaceIdx > 0) {
                    String surname = auth.substring(0, spaceIdx).trim();
                    String givenNames = auth.substring(spaceIdx + 1).trim();
                    sb.append("<name>\n");
                    sb.append("<surname>").append(XmlUtils.escape(surname)).append("</surname>\n");
                    sb.append("<given-names>").append(XmlUtils.escape(givenNames)).append("</given-names>\n");
                    sb.append("</name>\n");
                } else if (!auth.isBlank()) {
                    sb.append("<collab>").append(XmlUtils.escape(auth)).append("</collab>\n");
                }
            }
        }
        sb.append("</person-group>\n");
    }

    public void closeReferences() {
        if (started && !closed) {
            refList.append("</ref-list>\n");
            closed = true;
        }
    }

    public boolean hasContent() {
        return started && refCounter > 1;
    }

    public String build() {
        closeReferences();
        if (!started || refCounter == 1) {
            return "";
        }
        return "<back>\n" + refList + "</back>\n";
    }

    public void reset() {
        refList.setLength(0);
        started = false;
        closed = false;
        refCounter = 1;
    }
}
