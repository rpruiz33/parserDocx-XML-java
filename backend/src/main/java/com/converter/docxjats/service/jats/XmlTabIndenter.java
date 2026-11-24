package com.converter.docxjats.service.jats;

/**
 * Reformatea un documento XML usando tabulaciones (\t) para la indentación,
 * siguiendo el estilo de los archivos patrón de SciELO (1 tab por nivel).
 *
 * <p>Procesa el XML línea por línea: mantiene el contenido mixto (texto + tags
 * en la misma línea) sin modificarlo, sólo ajusta la cantidad de tabs al inicio
 * de cada línea.</p>
 */
public final class XmlTabIndenter {

    private XmlTabIndenter() {}

    /**
     * Aplica indentación con tabs al XML crudo.
     *
     * @param xml XML sin indentar (puede tener mezcla de espacios/tabs).
     * @return    XML reformateado con tabs uniformes.
     */
    public static String indent(String xml) {
        if (xml == null || xml.isBlank()) return xml;

        StringBuilder out = new StringBuilder();
        int depth = 0;

        for (String raw : xml.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // La declaración XML y el DOCTYPE siempre van al nivel 0
            if (line.startsWith("<?xml") || line.startsWith("<!DOCTYPE")) {
                out.append(line).append('\n');
                continue;
            }

            // Si la línea EMPIEZA con una etiqueta de cierre, bajar nivel ANTES de imprimir
            if (line.startsWith("</")) {
                depth = Math.max(0, depth - 1);
            }

            // Imprimir con la indentación actual
            out.append("\t".repeat(depth)).append(line).append('\n');

            // Decidir si subir nivel DESPUÉS de imprimir:
            // - Es etiqueta de apertura (comienza con < y no es </ ni <? ni <!)
            // - No es auto-cerrante (<tag/>)
            // - No tiene cierre en la misma línea (<tag>contenido</tag>)
            if (line.startsWith("<")
                    && !line.startsWith("</")
                    && !line.startsWith("<?")
                    && !line.startsWith("<!")
                    && !line.endsWith("/>")
                    && !closedOnSameLine(line)) {
                depth++;
            }
        }

        return out.toString();
    }

    /**
     * Determina si la etiqueta de apertura inicial de la línea se cierra en ella misma,
     * por ejemplo {@code <title>Introducción</title>} o
     * {@code <ext-link ...>url</ext-link>}.
     */
    private static boolean closedOnSameLine(String line) {
        if (!line.startsWith("<")) return false;
        int firstClose = line.indexOf('>');
        if (firstClose < 0) return false;

        int spaceIdx = line.indexOf(' ');
        int tagEnd = (spaceIdx > 0 && spaceIdx < firstClose) ? spaceIdx : firstClose;
        String tagName = line.substring(1, tagEnd).trim();
        if (tagName.isEmpty()) return false;

        return line.contains("</" + tagName + ">");
    }
}

