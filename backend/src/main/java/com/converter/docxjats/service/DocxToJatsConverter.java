package com.converter.docxjats.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
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

@Service
public class DocxToJatsConverter {

    private static final Logger log = LoggerFactory.getLogger(DocxToJatsConverter.class);

    public ConversionResult convert(InputStream docxStream, String originalFilename) throws IOException {
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
                log.warn("No se pudo parsear el front con DocxFrontParser, se usa uno vacío con título de archivo: {}",
                        e.getMessage());
                front = new JatsFrontBuilder();
                bodyStart = -1;
                bodyEnd = Integer.MAX_VALUE;
            }

            try (InputStream bodyStream = Files.newInputStream(tempFile);
                 XWPFDocument document = new XWPFDocument(bodyStream)) {

                ImageRegistry imageRegistry = new ImageRegistry();
                RunRenderer runRenderer = new RunRenderer(imageRegistry);

                JatsBodyBuilder body = new JatsBodyBuilder();
                JatsBackBuilder back = new JatsBackBuilder(runRenderer);

                // Parser del body
                DocxBodyParser bodyParser = new DocxBodyParser(document, body, runRenderer, imageRegistry, bodyStart, bodyEnd);
                bodyParser.parse();

                // Parser del back
                DocxBackParser backParser = new DocxBackParser(document, back, bodyEnd);
                backParser.parse();

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

    private String stripExtension(String filename) {
        if (filename == null) return "Documento sin título";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }
}
