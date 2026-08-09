package com.converter.docxjats.service.jats;

/**
 * Construye el bloque {@code <back>} del artículo JATS: por ahora, solo la
 * lista de referencias ({@code <ref-list>}), detectada a partir de un
 * encabezado "Referencias"/"Bibliografía"/"References" en el .docx.
 * Cada línea de texto dentro de esa sección se vuelca como una
 * {@code <mixed-citation>} sin parsear autor/año/revista.
 */
public class JatsBackBuilder {

    private final StringBuilder refList = new StringBuilder();
    private boolean started = false;
    private boolean closed = false;
    private int refCounter = 1;

    public void openReferences(String title) {
        if (started && !closed) {
            closeReferences();
        }
        refList.append("<ref-list>\n<title>").append(XmlUtils.escape(title)).append("</title>\n");
        started = true;
        closed = false;
    }

    public boolean isOpen() {
        return started && !closed;
    }

    public void appendReference(String text) {
        if (text == null || text.isBlank()) return;
        refList.append("<ref id=\"ref").append(refCounter++).append("\">\n")
               .append("<mixed-citation>").append(XmlUtils.escape(text)).append("</mixed-citation>\n")
               .append("</ref>\n");
    }

    public void closeReferences() {
        if (started && !closed) {
            refList.append("</ref-list>\n");
            closed = true;
        }
    }

    public boolean hasContent() {
        return started;
    }

    /** Cierra cualquier ref-list pendiente y devuelve el XML final del <back> (o "" si no hubo referencias). */
    public String build() {
        closeReferences();
        if (!hasContent()) return "";
        return "<back>\n" + refList + "</back>\n";
    }
}
