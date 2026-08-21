package com.converter.docxjats.service.jats;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construye el bloque {@code <back>} del artículo JATS.
 * Genera la estructura <back><ref-list>...</ref-list></back>.
 */
public class JatsBackBuilder {

    private static final Pattern REFERENCE_NUMBER = Pattern.compile("^\\s*(?:\\[(\\d+)\\]|(\\d+))[.)]?\\s*(.*)$");
    private static final Pattern DOI = Pattern.compile("(?i)\\b(10\\.\\d{4,9}/[-._;()/:a-z0-9]+)");
    private static final Pattern URL = Pattern.compile("https?://\\S+");

    private final StringBuilder refList = new StringBuilder();
    private boolean started = false;
    private boolean closed = false;
    private int refCounter = 1;

    public void openReferences(String title) {
        if (started && !closed) {
            closeReferences();
        }
        refList.append("<ref-list>\n<title>")
               .append(XmlUtils.escape(title))
               .append("</title>\n");
        started = true;
        closed = false;
    }

    public boolean isOpen() {
        return started && !closed;
    }

    public void appendReference(String text) {
        if (text == null || text.isBlank()) return;

        // Si se intenta agregar una referencia pero no se llamó a openReferences previamente,
        // se abre una sección por defecto.
        if (!started) {
            openReferences("Referencias");
        }

         Matcher numbered = REFERENCE_NUMBER.matcher(text);
         String referenceId = numbered.matches() && numbered.group(1) != null
              ? numbered.group(1)
              : numbered.matches() && numbered.group(2) != null ? numbered.group(2) : String.valueOf(refCounter);
         String citationText = numbered.matches() ? numbered.group(3) : text.trim();
         Matcher doi = DOI.matcher(citationText);
         Matcher url = URL.matcher(citationText);

         refList.append("<ref id=\"B").append(referenceId).append("\">\n")
             .append("<mixed-citation>").append(XmlUtils.escape(citationText)).append("</mixed-citation>\n");
         if (doi.find()) {
             refList.append("<pub-id pub-id-type=\"doi\">")
                 .append(XmlUtils.escape(doi.group(1)))
                 .append("</pub-id>\n");
         }
         if (url.find()) {
             refList.append("<ext-link ext-link-type=\"uri\" xlink:href=\"")
                 .append(XmlUtils.escape(url.group()))
                 .append("\">")
                 .append(XmlUtils.escape(url.group()))
                 .append("</ext-link>\n");
         }
         refList.append("</ref>\n");
         refCounter++;
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

    /**
     * Devuelve el bloque <back> formateado.
     * Si no se agregaron referencias, devuelve un bloque <back/> vacío 
     * o genera la salida normal si hubo contenido.
     */
    public String build() {
        closeReferences();
        
        // Si no se inició la lista o no se agregó ninguna referencia
        if (!started || refCounter == 1) {
            return ""; // O puedes retornar "<back/>\n" si tu esquema exige la etiqueta
        }

        return "<back>\n" + refList + "</back>\n";
    }

    /**
     * Permite reiniciar el builder para procesar un nuevo documento.
     */
    public void reset() {
        refList.setLength(0);
        started = false;
        closed = false;
        refCounter = 1;
    }
}