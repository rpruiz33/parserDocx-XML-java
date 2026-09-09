package com.converter.docxjats.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import com.converter.docxjats.service.jats.ImageRegistry;
import com.converter.docxjats.service.jats.JatsBodyBuilder;
import com.converter.docxjats.service.jats.RunRenderer;

public class DocxBodyParser {

    private static final Pattern HEADING_PATTERN =
            Pattern.compile("(?i)^(heading|t[ií]tulo)\\s*([1-6])$");
    private static final Pattern QUOTE_STYLE =
            Pattern.compile("(?i)(quote|cita|block.?quote|epigraph)");
    private static final Pattern SECTION_TYPE =
            Pattern.compile("(?i)^(introducci[oó]n|introduction|metodolog[ií]a|m[eé]todos|methods|resultados|results|discusi[oó]n|discussion|conclusiones|conclusion).*$");

    private final XWPFDocument document;
    private final JatsBodyBuilder bodyBuilder;
    private final RunRenderer runRenderer;
    private final ImageRegistry imageRegistry;
    private final int bodyStart;
    private final int bodyEnd;

    public DocxBodyParser(XWPFDocument document, JatsBodyBuilder bodyBuilder,
                         RunRenderer runRenderer, ImageRegistry imageRegistry,
                         int bodyStart, int bodyEnd) {
        this.document = document;
        this.bodyBuilder = bodyBuilder;
        this.runRenderer = runRenderer;
        this.imageRegistry = imageRegistry;
        this.bodyStart = bodyStart;
        this.bodyEnd = bodyEnd;
    }

    public void parse() {
        List<IBodyElement> elements = document.getBodyElements();
        int paraOrdinal = -1;

        for (IBodyElement element : elements) {

            if (element instanceof XWPFParagraph paragraph) {
                paraOrdinal++;
                String rawText = paragraph.getText();
                String trimmedText = rawText != null ? rawText.trim() : "";

                // Saltear rango consumido por front
                if (bodyStart >= 0 && paraOrdinal < bodyStart) {
                    continue;
                }
                // Saltear cierre consumido por front (excepto referencias)
                if (bodyEnd < Integer.MAX_VALUE && paraOrdinal >= bodyEnd) {
                    continue;
                }

                // Saltear párrafos vacíos
                if (trimmedText.isEmpty() && !isImageOnlyParagraph(paragraph)) {
                    continue;
                }

                processParagraph(paragraph, trimmedText);

            } else if (element instanceof XWPFTable table) {
                bodyBuilder.appendTable(table, null, null, runRenderer);
            }
        }
    }

    private void processParagraph(XWPFParagraph paragraph, String trimmedText) {
        String styleName = resolveStyleName(paragraph);
        String normalizedText = normalizeHeading(trimmedText);

        // Detección de encabezados
        Matcher hm = styleName != null ? HEADING_PATTERN.matcher(styleName) : null;
        if (hm != null && hm.matches()) {
            int level = Integer.parseInt(hm.group(2));
            bodyBuilder.openSection(level, trimmedText, resolveSectionType(trimmedText));
            return;
        }

        // Procesamiento de imágenes
        if (isImageOnlyParagraph(paragraph) && trimmedText.isBlank()) {
            for (XWPFRun run : paragraph.getRuns()) {
                for (XWPFPicture picture : run.getEmbeddedPictures()) {
                    String filename = imageRegistry.register(picture);
                    if (filename != null) {
                        bodyBuilder.appendFigure(filename);
                    }
                }
            }
            return;
        }

        // Procesamiento de listas
        String numId = getNumId(paragraph);
        if (numId != null) {
            String listType = resolveListType(paragraph);
            String runsXml = runRenderer.render(paragraph);
            bodyBuilder.appendListItem(listType, numId, runsXml);
            return;
        } else {
            bodyBuilder.closeListIfOpen();
        }

        // Procesamiento de citas
        String runsXml = runRenderer.render(paragraph);
        if (styleName != null && QUOTE_STYLE.matcher(styleName).find()) {
            bodyBuilder.appendDispQuote(runsXml);
        } else {
            bodyBuilder.appendParagraph(runsXml);
        }
    }

    private String resolveStyleName(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null || document.getStyles() == null) {
            return null;
        }
        XWPFStyle style = document.getStyles().getStyle(styleId);
        return style != null ? style.getName() : null;
    }

    private String getNumId(XWPFParagraph paragraph) {
        return paragraph.getNumID() != null ? paragraph.getNumID().toString() : null;
    }

    private String resolveListType(XWPFParagraph paragraph) {
        try {
            XWPFNumbering numbering = document.getNumbering();
            if (numbering == null || paragraph.getNumID() == null) {
                return "bullet";
            }
            XWPFNum num = numbering.getNum(paragraph.getNumID());
            if (num == null) {
                return "bullet";
            }
            var abstractId = num.getCTNum().getAbstractNumId().getVal();
            XWPFAbstractNum abstractNum = numbering.getAbstractNum(abstractId);
            if (abstractNum != null && abstractNum.getAbstractNum().getLvlArray(0) != null) {
                String fmt = abstractNum.getAbstractNum().getLvlArray(0).getNumFmt().getVal().toString();
                if (fmt.toLowerCase(Locale.ROOT).contains("decimal")) {
                    return "order";
                }
            }
        } catch (Exception ignored) {
        }
        return "bullet";
    }

    private boolean isImageOnlyParagraph(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (!run.getEmbeddedPictures().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHeading(String text) {
        if (text == null) return "";
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).trim();
    }

    private String resolveSectionType(String title) {
        if (title == null) return null;
        Matcher matcher = SECTION_TYPE.matcher(title.trim());
        if (!matcher.matches()) return null;
        String normalized = normalizeHeading(title);
        if (normalized.startsWith("introduccion") || normalized.startsWith("introduction")) return "intro";
        if (normalized.startsWith("metodologia") || normalized.startsWith("metodos") || normalized.startsWith("methods")) return "methods";
        if (normalized.startsWith("resultado") || normalized.startsWith("results")) return "results";
        if (normalized.startsWith("discusion") || normalized.startsWith("discussion")) return "discussion";
        if (normalized.startsWith("conclusion")) return "conclusions";
        return null;
    }
}
