package com.converter.docxjats.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import com.converter.docxjats.service.jats.JatsBackBuilder;

public class DocxBackParser {

    private final XWPFDocument document;
    private final JatsBackBuilder backBuilder;
    private final int bodyEnd;

    public DocxBackParser(XWPFDocument document, JatsBackBuilder backBuilder, int bodyEnd) {
        this.document = document;
        this.backBuilder = backBuilder;
        this.bodyEnd = bodyEnd;
    }

    public void parse() {
        List<IBodyElement> elements = document.getBodyElements();
        int paraOrdinal = -1;
        boolean inReferences = false;

        for (IBodyElement element : elements) {

            if (element instanceof XWPFParagraph paragraph) {
                paraOrdinal++;

                // Procesar solo elementos después del bodyEnd
                if (bodyEnd < Integer.MAX_VALUE && paraOrdinal < bodyEnd) {
                    continue;
                }

                String rawText = paragraph.getText();
                String trimmedText = rawText != null ? rawText.trim() : "";

                if (trimmedText.isEmpty()) {
                    continue;
                }

                String normalizedText = normalizeHeading(trimmedText);

                // Detección de sección de referencias
                if (isReferencesHeading(normalizedText)) {
                    if (inReferences) {
                        backBuilder.closeReferences();
                    }
                    backBuilder.openReferences(trimmedText);
                    inReferences = true;
                    continue;
                }

                // Si estamos en referencias, agregar como referencia
                if (inReferences) {
                    backBuilder.appendReference(trimmedText);
                }

            } else if (element instanceof XWPFTable table) {
                if (inReferences) {
                    backBuilder.closeReferences();
                    inReferences = false;
                }
            }
        }

        if (inReferences) {
            backBuilder.closeReferences();
        }
    }

    private String normalizeHeading(String text) {
        if (text == null) return "";
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isReferencesHeading(String normalized) {
        return normalized.startsWith("referenc")
                || normalized.startsWith("bibliograf")
                || normalized.equals("referencias bibliograficas");
    }
}
