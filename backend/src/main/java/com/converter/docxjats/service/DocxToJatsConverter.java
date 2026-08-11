package com.converter.docxjats.service;

import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.stereotype.Service;

import com.converter.docxjats.dto.ConversionResult;
import com.converter.docxjats.service.jats.ImageRegistry;
import com.converter.docxjats.service.jats.JatsBackBuilder;
import com.converter.docxjats.service.jats.JatsBodyBuilder;
import com.converter.docxjats.service.jats.JatsFrontBuilder;
import com.converter.docxjats.service.jats.RunRenderer;

@Service
public class DocxToJatsConverter {

    private static final Pattern HEADING_PATTERN =
            Pattern.compile("(?i)^(heading|t[ií]tulo)\\s*([1-6])$");
    private static final Pattern TITLE_STYLE_PATTERN =
            Pattern.compile("(?i)^(title|t[ií]tulo)$");

    private enum Mode { BODY, ABSTRACT, AUTHORS, REFERENCES }

    public ConversionResult convert(InputStream docxStream, String originalFilename) throws IOException {
        try (XWPFDocument document = new XWPFDocument(docxStream)) {

            ImageRegistry imageRegistry = new ImageRegistry();
            RunRenderer runRenderer = new RunRenderer(imageRegistry);

            JatsFrontBuilder front = new JatsFrontBuilder();
            JatsBodyBuilder body = new JatsBodyBuilder();
            JatsBackBuilder back = new JatsBackBuilder();

            Mode mode = Mode.BODY;
            List<IBodyElement> elements = document.getBodyElements();

            for (IBodyElement element : elements) {

                if (element instanceof XWPFParagraph paragraph) {
                    String styleName = resolveStyleName(document, paragraph);
                    String rawText = paragraph.getText();
                    String trimmedText = rawText != null ? rawText.trim() : "";

                    // Si el párrafo está completamente vacío, lo salta sin alterar estados
                    if (trimmedText.isEmpty() && !isImageOnlyParagraph(paragraph)) {
                        continue;
                    }

                    String normalizedText = normalizeHeading(trimmedText);

                    // --- Título / subtítulo principal -> FRONT ---
                    if (!front.hasTitle() && styleName != null && TITLE_STYLE_PATTERN.matcher(styleName).matches()) {
                        front.setTitle(trimmedText.isBlank() ? originalFilename : trimmedText);
                        continue;
                    }
                    if (styleName != null && (styleName.equalsIgnoreCase("Subtitle") || styleName.equalsIgnoreCase("Subt\u00edtulo"))) {
                        front.setSubtitle(trimmedText);
                        continue;
                    }
                    if (processFrontMetadata(trimmedText, front)) {
                         continue; 
                   }

                    // --- DETECCIÓN PRIORITARIA DE SECCIONES (Especialmente Referencias/Back) ---
                    // Se verifica el texto primero, independiente de si Word le asignó estilo Heading o Normal.
                    if (isReferencesHeading(normalizedText)) {
                        if (mode == Mode.REFERENCES) back.closeReferences();
                        back.openReferences(trimmedText);
                        mode = Mode.REFERENCES;
                        continue;
                    }

                    if (isAbstractHeading(normalizedText)) {
                        if (mode == Mode.REFERENCES) back.closeReferences();
                        mode = Mode.ABSTRACT;
                        continue;
                    }

                    if (isAuthorsHeading(normalizedText)) {
                        if (mode == Mode.REFERENCES) back.closeReferences();
                        mode = Mode.AUTHORS;
                        continue;
                    }

                    // --- ENCABEZADOS DE TIPO HEADING GENERAL (Secciones del Body) ---
                    Matcher hm = styleName != null ? HEADING_PATTERN.matcher(styleName) : null;
                    if (hm != null && hm.matches()) {
                        int level = Integer.parseInt(hm.group(2));
                        
                        // Si veníamos de referencias u otro modo especial y encontramos un heading normal, salimos
                        if (mode == Mode.REFERENCES) back.closeReferences();
                        mode = Mode.BODY;

                        body.openSection(level, trimmedText);
                        continue;
                    }

                    // --- PROCESAMIENTO SEGÚN MODO ACTIVO ---

                    // 1. Modo REFERENCIAS -> envía líneas directamente a JatsBackBuilder
                    if (mode == Mode.REFERENCES) {
                        back.appendReference(trimmedText);
                        continue;
                    }

                    // 2. Modo AUTORES -> asigna metadata de autores
                    if (mode == Mode.AUTHORS) {
                        for (String segment : trimmedText.split(";")) {
                            String trimmed = segment.trim();
                            if (trimmed.isEmpty()) continue;
                            String[] parts = trimmed.split("\\s*[-\u2013\u2014]\\s*", 2);
                            if (parts.length == 2) {
                                front.appendAuthor(parts[0], parts[1]);
                            } else {
                                front.appendAuthor(trimmed, null);
                            }
                        }
                        continue;
                    }

                    // 3. Listas (viñetas / numeradas)
                    String numId = getNumId(paragraph);
                    if (numId != null) {
                        String listType = resolveListType(document, paragraph);
                        String runsXml = runRenderer.render(paragraph);
                        if (mode == Mode.ABSTRACT) {
                            front.appendAbstractParagraph(runsXml);
                        } else {
                            body.appendListItem(listType, numId, runsXml);
                        }
                        continue;
                    } else {
                        body.closeListIfOpen();
                    }

                    // 4. Imágenes aisladas
                    if (isImageOnlyParagraph(paragraph) && trimmedText.isBlank()) {
                        for (XWPFRun run : paragraph.getRuns()) {
                            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                                String filename = imageRegistry.register(picture);
                                if (filename != null) {
                                    body.appendFigure(filename);
                                }
                            }
                        }
                        continue;
                    }

                    // 5. Párrafos normales -> ABSTRACT o BODY
                    String runsXml = runRenderer.render(paragraph);
                    if (mode == Mode.ABSTRACT) {
                        front.appendAbstractParagraph(runsXml);
                    } else {
                        body.appendParagraph(runsXml);
                    }

                } else if (element instanceof XWPFTable table) {
                    if (mode == Mode.REFERENCES) {
                        back.closeReferences();
                        mode = Mode.BODY;
                    }
                    body.appendTable(table);
                }
            }

            // Asegura el cierre de las referencias si el documento termina en esa sección
            if (mode == Mode.REFERENCES) {
                back.closeReferences();
            }

            String fallbackTitle = stripExtension(originalFilename);
            String xml = assembleArticle(front.build(fallbackTitle), body.build(), back.build());

            ConversionResult result = new ConversionResult(xml);
            imageRegistry.getImages().forEach(result::addImage);
            return result;
        }
    }

    private String assembleArticle(String frontXml, String bodyXml, String backXml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE article PUBLIC \"-//NLM//DTD JATS (Z39.96) Journal Publishing DTD v1.3 20210610//EN\" ")
          .append("\"JATS-journalpublishing1.dtd\">\n");
        sb.append("<article xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
          .append("article-type=\"research-article\" dtd-version=\"1.3\">\n");
        sb.append(frontXml);
        sb.append(bodyXml);
        sb.append(backXml);
        sb.append("</article>\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Métodos Auxiliares
    // ---------------------------------------------------------------

    private String resolveStyleName(XWPFDocument document, XWPFParagraph paragraph) {
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

    private String resolveListType(XWPFDocument document, XWPFParagraph paragraph) {
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
            // Fallback por defecto
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

    private boolean isAbstractHeading(String normalized) {
        return normalized.equals("resumen") || normalized.equals("abstract") || normalized.equals("resumo");
    }

    private boolean isAuthorsHeading(String normalized) {
        return normalized.equals("autor") || normalized.equals("autores") || normalized.equals("authors") || normalized.equals("author");
    }

    private boolean isReferencesHeading(String normalized) {
        return normalized.startsWith("referenc") 
                || normalized.startsWith("bibliograf")
                || normalized.equals("referencias bibliograficas");
    }

    private String stripExtension(String filename) {
        if (filename == null) return "Documento sin título";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }
    private boolean processFrontMetadata(String line, JatsFrontBuilder front) {
    if (line == null || line.isBlank()) return false;
    String text = line.trim();

    // Detección de versión SPS / SciELO
    if (text.matches("(?i)^sps-\\d+\\.\\d+$")) {
        front.setSpsVersion(text);
        return true;
    }
    // Detección de idioma de 2 letras
    if (text.matches("(?i)^(es|en|pt)$")) {
        front.setLang(text.toLowerCase());
        return true;
    }
    // Detección de DOI
    if (text.contains("10.") && text.contains("/")) {
        front.setArticleIdDoi(text.replaceAll("(?i)^https?://doi\\.org/", ""));
        return true;
    }
    // Detección de ISSNs (ej: 1669-2381)
    if (text.matches("^\\d{4}-\\d{3}[0-9X]$")) {
        front.setIssnPrint(text);
        return true;
    }
    // Detección de Nombre/Abreviatura de Revista o Universidad
    if (text.equalsIgnoreCase("Salud Colectiva") || text.equalsIgnoreCase("Salud Colect")) {
        front.setJournalName("Salud Colectiva");
        front.setJournalAbbrev("Salud Colect");
        return true;
    }
    if (text.equalsIgnoreCase("scol")) {
        front.setJournalId("scol");
        return true;
    }
    if (text.equalsIgnoreCase("Universidad Nacional de Lanús")) {
        front.setPublisherName(text);
        return true;
    }

    return false;
}
}