package com.converter.docxjats.service.jats;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/**
 * Convierte los runs de un párrafo de Word (negrita, cursiva, subrayado,
 * super/subíndice, imágenes inline) al marcado JATS equivalente.
 * Usado tanto por el body como por el resumen (front) y las referencias (back).
 */
public class RunRenderer {

    private static final Pattern BIBLIOGRAPHIC_CITATION =
            Pattern.compile("(\\d+)|([^\\d]+)");

    private final ImageRegistry imageRegistry;

    public RunRenderer(ImageRegistry imageRegistry) {
        this.imageRegistry = imageRegistry;
    }

    public String render(XWPFParagraph paragraph) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();

            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                String filename = imageRegistry.register(picture);
                if (filename != null) {
                    sb.append("<inline-graphic xlink:href=\"images/").append(filename).append("\"/>");
                }
            }

            if (text == null || text.isEmpty()) {
                continue;
            }
            String escaped = XmlUtils.escape(text);
            if (run.isBold()) {
                escaped = "<bold>" + escaped + "</bold>";
            }
            if (run.isItalic()) {
                escaped = "<italic>" + escaped + "</italic>";
            }
            if (run.getUnderline() != null && run.getUnderline() != UnderlinePatterns.NONE) {
                escaped = "<underline>" + escaped + "</underline>";
            }
            // Se evita referenciar directamente la clase de esquema STVerticalAlignRun
            // (vive en poi-ooxml-full); comparamos por texto para evitar problemas de classpath.
            Object valign = run.getVerticalAlignment();
            String valignStr = valign == null ? "" : valign.toString().toLowerCase(Locale.ROOT);
            if (valignStr.contains("superscript")) {
                escaped = renderSuperscript(escaped, text);
            } else if (valignStr.contains("subscript")) {
                escaped = "<sub>" + escaped + "</sub>";
            }
            sb.append(escaped);
        }
        return sb.toString();
    }

    private String renderSuperscript(String escaped, String originalText) {
        if (!originalText.matches(".*\\d.*")) {
            return "<sup>" + escaped + "</sup>";
        }

        Matcher matcher = BIBLIOGRAPHIC_CITATION.matcher(originalText);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.matches("\\d+")) {
                result.append("<xref ref-type=\"bibr\" rid=\"B")
                        .append(token)
                        .append("\"><sup>")
                        .append(token)
                        .append("</sup></xref>");
            } else {
                result.append("<sup>")
                        .append(XmlUtils.escape(token))
                        .append("</sup>");
            }
        }
        return result.toString();
    }
}
