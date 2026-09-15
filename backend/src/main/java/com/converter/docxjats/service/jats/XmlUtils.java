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

    public static String escapeAttribute(String text) {
        if (text == null) return "";
        return escape(text)
                .replace("“", "&quot;")
                .replace("”", "&quot;")
                .replace("‘", "&apos;")
                .replace("’", "&apos;");
    }
}
