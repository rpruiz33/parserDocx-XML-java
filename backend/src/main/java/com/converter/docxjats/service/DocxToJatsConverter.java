package com.converter.docxjats.service;

import com.converter.docxjats.dto.ConversionResult;
import com.converter.docxjats.service.jats.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquesta la conversión de un documento Word (.docx) a XML JATS.
 * <p>
 * Recorre los elementos del .docx en orden (párrafos y tablas) y, según el
 * estilo detectado (Título, Heading N, listas, etc.), delega la generación
 * del XML en tres módulos independientes:
 * <ul>
 *   <li>{@link JatsFrontBuilder} — título, subtítulo y resumen (front-matter)</li>
 *   <li>{@link JatsBodyBuilder} — secciones, párrafos, listas, tablas, figuras</li>
 *   <li>{@link JatsBackBuilder} — lista de referencias (back-matter)</li>
 * </ul>
 * Esta clase NO genera texto XML directamente: solo decide a qué módulo
 * y con qué datos llamar. Eso mantiene la lógica de parseo del .docx
 * separada de la lógica de armado de cada bloque JATS.
 */
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
                    String text = paragraph.getText();

                    // --- Título / subtítulo del artículo -> módulo FRONT ---
                    if (!front.hasTitle() && styleName != null && TITLE_STYLE_PATTERN.matcher(styleName).matches()) {
                        front.setTitle(text.isBlank() ? originalFilename : text.trim());
                        continue;
                    }
                    if (styleName != null
                            && (styleName.equalsIgnoreCase("Subtitle") || styleName.equalsIgnoreCase("Subt\u00edtulo"))) {
                        front.setSubtitle(text.trim());
                        continue;
                    }

                    // --- Encabezados: deciden si entramos a ABSTRACT, REFERENCES o un <sec> del BODY ---
                    Matcher hm = styleName != null ? HEADING_PATTERN.matcher(styleName) : null;
                    if (hm != null && hm.matches()) {
                        int level = Integer.parseInt(hm.group(2));
                        String headingText = text.trim();
                        String normalized = normalizeHeading(headingText);

                        if (isAbstractHeading(normalized)) {
                            if (mode == Mode.REFERENCES) back.closeReferences();
                            mode = Mode.ABSTRACT;
                            continue;
                        }
                        if (isAuthorsHeading(normalized)) {
                            if (mode == Mode.REFERENCES) back.closeReferences();
                            mode = Mode.AUTHORS;
                            continue;
                        }
                        if (isReferencesHeading(normalized)) {
                            back.openReferences(headingText);
                            mode = Mode.REFERENCES;
                            continue;
                        }

                        // Heading "normal": volvemos al BODY si veníamos de resumen/referencias
                        if (mode == Mode.REFERENCES) back.closeReferences();
                        mode = Mode.BODY;

                        body.openSection(level, headingText);
                        continue;
                    }

                    // --- Referencias / Bibliografía heading (no coincide con HEADING_PATTERN) ---
                    if (styleName != null && isReferencesHeading(normalizeHeading(text.trim()))) {
                        back.openReferences(text.trim());
                        mode = Mode.REFERENCES;
                        continue;
                    }

                    // --- Modo REFERENCIAS -> módulo BACK ---
                    if (mode == Mode.REFERENCES) {
                        back.appendReference(text.trim());
                        continue;
                    }

                    // --- Modo AUTORES -> módulo FRONT (contrib-group) ---
                    if (mode == Mode.AUTHORS) {
                        if (!text.isBlank()) {
                            for (String segment : text.split(";")) {
                                String trimmed = segment.trim();
                                if (trimmed.isEmpty()) continue;
                                // Formato admitido: "Nombre Apellido - Afiliación" (separador -, – o —)
                                String[] parts = trimmed.split("\\s*[-\u2013\u2014]\\s*", 2);
                                if (parts.length == 2) {
                                    front.appendAuthor(parts[0], parts[1]);
                                } else {
                                    front.appendAuthor(trimmed, null);
                                }
                            }
                        }
                        continue;
                    }

                    // --- Listas (viñetas / numeradas) ---
                    String numId = getNumId(paragraph);
                    if (numId != null) {
                        String listType = resolveListType(document, paragraph);
                        String runsXml = runRenderer.render(paragraph);
                        if (mode == Mode.ABSTRACT) {
                            // JATS no admite <list> dentro de <abstract> en la mayoría de perfiles;
                            // lo volcamos como párrafo simple para no romper el esquema.
                            front.appendAbstractParagraph(runsXml);
                        } else {
                            body.appendListItem(listType, numId, runsXml);
                        }
                        continue;
                    } else {
                        body.closeListIfOpen();
                    }

                    // --- Párrafo compuesto SOLO por una imagen -> <fig> en el BODY ---
                    if (isImageOnlyParagraph(paragraph) && text.isBlank()) {
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

                    if (text.isBlank()) {
                        continue; // párrafo vacío, se ignora
                    }

                    // --- Párrafo normal -> FRONT (si estamos en abstract) o BODY ---
                    String runsXml = runRenderer.render(paragraph);
                    if (mode == Mode.ABSTRACT) {
                        front.appendAbstractParagraph(runsXml);
                    } else {
                        body.appendParagraph(runsXml);
                    }

                } else if (element instanceof XWPFTable table) {
                    body.appendTable(table);
                }
            }

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

    /** Envuelve los tres bloques (front/body/back) ya generados en el <article> final. */
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
    // Utilidades de lectura del .docx (estilos, numeración, imágenes)
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
            // Si algo falla al inspeccionar la numeración, asumimos viñetas.
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

    /** Quita tildes, pasa a minúsculas y recorta espacios (para comparar headings de forma tolerante). */
    private String normalizeHeading(String text) {
        if (text == null) return "";
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isAbstractHeading(String normalized) {
        return normalized.contains("resumen") || normalized.contains("abstract") || normalized.contains("resumo");
    }

    private boolean isAuthorsHeading(String normalized) {
        return normalized.contains("autor") || normalized.contains("author");
    }

    private boolean isReferencesHeading(String normalized) {
        return normalized.contains("referenc")   // referencia(s), reference(s)
                || normalized.contains("bibliograf"); // bibliografia, bibliography
    }

    private String stripExtension(String filename) {
        if (filename == null) return "Documento sin t\u00edtulo";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }
}
