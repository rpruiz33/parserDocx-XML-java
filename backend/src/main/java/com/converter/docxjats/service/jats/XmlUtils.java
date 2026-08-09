package com.converter.docxjats.service.jats;

/** Utilidades XML compartidas por los módulos front/body/back. */
public final class XmlUtils {

    private XmlUtils() {
    }

    public static String escape(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
