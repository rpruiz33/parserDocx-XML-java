package com.converter.docxjats.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.converter.docxjats.dto.ConversionResult;
import com.converter.docxjats.service.jats.DocxFrontParser;
import com.converter.docxjats.service.jats.ImageRegistry;
import com.converter.docxjats.service.jats.JatsBackBuilder;
import com.converter.docxjats.service.jats.JatsBodyBuilder;
import com.converter.docxjats.service.jats.JatsFrontBuilder;
import com.converter.docxjats.service.jats.RunRenderer;

/**
 * Orquesta la conversión completa .docx -&gt; JATS.
 *
 * <p>El {@code <front>} lo arma {@link DocxFrontParser} (metadatos de revista,
 * título, autores + ORCID + afiliaciones reales, resumen/abstract, palabras
 * clave, financiamiento, conflicto de intereses, contribución autoral e
 * historia editorial), leyendo directamente {@code word/document.xml} del
 * .docx. Esta clase recorre el documento una <b>segunda vez</b> con Apache
 * POI, pero <b>solo</b> para {@code <body>} y {@code <back>} (referencias):
 * el rango de párrafos que ya consumió {@link DocxFrontParser} —al principio
 * (metadatos/título/autores/afiliaciones/resumen/keywords) y al final
 * (financiamiento/conflicto/contribución/historia)— se salta explícitamente
 * para no duplicar contenido, usando los límites que expone el parser
 * ({@link DocxFrontParser#getBodyStartParaIndex()} /
 * {@link DocxFrontParser#getBodyEndParaIndex()}).
 *
 * <p>Los párrafos de "Referencias bibliográficas" quedan fuera de ese rango
 * de body pero SÍ se procesan igual (vía {@link #isReferencesHeading}) porque
 * quien arma el {@code <ref-list>} real sigue siendo {@link JatsBackBuilder};
 * {@link DocxFrontParser} solo cuenta cuántas hay, para {@code ref-count}.
 */
@Service
public class DocxToJatsConverter {

    private static final Logger log = LoggerFactory.getLogger(DocxToJatsConverter.class);

    private static final Pattern HEADING_PATTERN =
            Pattern.compile("(?i)^(heading|t[ií]tulo)\\s*([1-6])$");
        private static final Pattern QUOTE_STYLE =
            Pattern.compile("(?i)(quote|cita|block.?quote|epigraph)");
        private static final Pattern SECTION_TYPE =
            Pattern.compile("(?i)^(introducci[oó]n|introduction|metodolog[ií]a|m[eé]todos|methods|resultados|results|discusi[oó]n|discussion|conclusiones|conclusion).*$");

    private enum Mode { BODY, REFERENCES }

    public ConversionResult convert(InputStream docxStream, String originalFilename) throws IOException {
        // DocxFrontParser lee el .docx como zip (ZipFile), así que necesita un
        // File real; XWPFDocument puede leer el mismo stream desde ese File
        // sin problema. Se bufferea una sola vez a un temporal.
        Path tempFile = Files.createTempFile("docxjats-", ".docx");
        try {
            Files.copy(docxStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            JatsFrontBuilder front;
            int bodyStart;
            int bodyEnd;
            int headingWarningsCount = 0;
            try {
                DocxFrontParser frontParser = new DocxFrontParser();
                front = frontParser.parse(tempFile.toFile());
                bodyStart = frontParser.getBodyStartParaIndex();
                bodyEnd = frontParser.getBodyEndParaIndex();
                headingWarningsCount = frontParser.getWarnings().size();
                for (DocxFrontParser.ParseWarning w : frontParser.getWarnings()) {
                    log.warn("[front-parser][{}] {}", w.section(), w.message());
                }
            } catch (Exception e) {
                // El front es best-effort: si el .docx no sigue la plantilla
                // esperada, no tiene sentido tirar abajo toda la conversión.
                log.warn("No se pudo parsear el front con DocxFrontParser, se usa uno vacío con título de archivo: {}",
                        e.getMessage());
                front = new JatsFrontBuilder();
                bodyStart = -1; // -1 => no saltear nada (procesar todo el documento como body)
                bodyEnd = Integer.MAX_VALUE;
            }

            try (InputStream bodyStream = Files.newInputStream(tempFile);
                 XWPFDocument document = new XWPFDocument(bodyStream)) {

                ImageRegistry imageRegistry = new ImageRegistry();
                RunRenderer runRenderer = new RunRenderer(imageRegistry);

                JatsBodyBuilder body = new JatsBodyBuilder();
                JatsBackBuilder back = new JatsBackBuilder();

                Mode mode = Mode.BODY;
                List<IBodyElement> elements = document.getBodyElements();
                int paraOrdinal = -1; // solo cuenta párrafos (<w:p>), igual que DocxFrontParser

                for (IBodyElement element : elements) {

                    if (element instanceof XWPFParagraph paragraph) {
                        paraOrdinal++;
                        String rawText = paragraph.getText();
                        String trimmedText = rawText != null ? rawText.trim() : "";
                        String normalizedText = normalizeHeading(trimmedText);

                        boolean isReferencesHeadingLine = isReferencesHeading(normalizedText);

                        // Rango ya consumido por el front: metadatos, título, autores,
                        // afiliaciones, resumen/abstract, keywords -> saltear.
                        if (bodyStart >= 0 && paraOrdinal < bodyStart) {
                            continue;
                        }
                        // Cierre ya consumido por el front (financiamiento, conflicto,
                        // contribución, historia), EXCEPTO la sección de referencias,
                        // que sigue armándose acá con JatsBackBuilder.
                        if (bodyEnd < Integer.MAX_VALUE && paraOrdinal >= bodyEnd
                                && mode != Mode.REFERENCES && !isReferencesHeadingLine) {
                            continue;
                        }

                        // Si el párrafo está completamente vacío, lo salta sin alterar estados
                        if (trimmedText.isEmpty() && !isImageOnlyParagraph(paragraph)) {
                            continue;
                        }

                        if (isReferencesHeadingLine) {
                            if (mode == Mode.REFERENCES) back.closeReferences();
                            back.openReferences(trimmedText);
                            mode = Mode.REFERENCES;
                            continue;
                        }

                        String styleName = resolveStyleName(document, paragraph);
                        Matcher hm = styleName != null ? HEADING_PATTERN.matcher(styleName) : null;
                        if (hm != null && hm.matches()) {
                            int level = Integer.parseInt(hm.group(2));
                            if (mode == Mode.REFERENCES) back.closeReferences();
                            mode = Mode.BODY;
                            body.openSection(level, trimmedText, sectionType(trimmedText));
                            continue;
                        }

                        if (mode == Mode.REFERENCES) {
                            back.appendReference(trimmedText);
                            continue;
                        }

                        String numId = getNumId(paragraph);
                        if (numId != null) {
                            String listType = resolveListType(document, paragraph);
                            String runsXml = runRenderer.render(paragraph);
                            body.appendListItem(listType, numId, runsXml);
                            continue;
                        } else {
                            body.closeListIfOpen();
                        }

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

                        String runsXml = runRenderer.render(paragraph);
                        if (styleName != null && QUOTE_STYLE.matcher(styleName).find()) {
                            body.appendDispQuote(runsXml);
                        } else {
                            body.appendParagraph(runsXml);
                        }

                    } else if (element instanceof XWPFTable table) {
                        if (mode == Mode.REFERENCES) {
                            back.closeReferences();
                            mode = Mode.BODY;
                        }
                        body.appendTable(table, null, null, runRenderer);
                    }
                }

                if (mode == Mode.REFERENCES) {
                    back.closeReferences();
                }

                String fallbackTitle = stripExtension(originalFilename);
                String xml = assembleArticle(front.build(fallbackTitle), body.build(), back.build());

                ConversionResult result = new ConversionResult(xml);
                result.setHeadingWarnings(headingWarningsCount);
                imageRegistry.getImages().forEach(result::addImage);
                return result;
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String assembleArticle(String frontXml, String bodyXml, String backXml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                sb.append("<!DOCTYPE article PUBLIC \"-//NLM//DTD JATS (Z39.96) Journal Publishing DTD v1.1 20151215//EN\" ")
                    .append("\"https://jats.nlm.nih.gov/publishing/1.1/JATS-journalpublishing1.dtd\">\n");
                sb.append("<article article-type=\"research-article\" dtd-version=\"1.1\" specific-use=\"sps-1.9\" xml:lang=\"es\" ")
                    .append("xmlns:mml=\"http://www.w3.org/1998/Math/MathML\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n");
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

    private String sectionType(String title) {
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
