package com.converter.docxjats.service.jats;

/**
 * Construye el bloque {@code <back>} del artículo JATS.
 * Genera la estructura <back><ref-list>...</ref-list></back>.
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