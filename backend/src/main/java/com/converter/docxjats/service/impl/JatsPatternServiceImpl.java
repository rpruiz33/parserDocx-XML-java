package com.converter.docxjats.service.impl;

import com.converter.docxjats.dto.JatsPatternResponse;
import com.converter.docxjats.service.interfaces.IJatsPatternService;
import com.converter.docxjats.service.jats.JatsValidator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Implementación del servicio de localización y normalización de archivos XML JATS patrón
 * desde el directorio {@code archivosXMLPatron/}.
 * Cumple con ISP al implementar la interfaz IJatsPatternService.
 */
@Service
public class JatsPatternServiceImpl implements IJatsPatternService {

    /** Directorios candidatos donde buscar los XML patrón, en orden de prioridad. */
    private static final List<String> SEARCH_ROOTS = Arrays.asList(
            "../archivosXMLPatron",
            "archivosXMLPatron",
            "/home/beto/Desktop/practica PP/parserDocx-XML-java/archivosXMLPatron"
    );

    @Override
    public String resolveArticleId(String articleId, Map<String, String> payload) {
        String id = articleId;
        if ((id == null || id.isBlank()) && payload != null) {
            id = payload.get("articleId");
            if (id == null) {
                id = payload.get("id");
            }
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Debe proporcionar el identificador del artículo en el parámetro 'articleId' o en el payload JSON.");
        }
        return id.trim();
    }

    @Override
    public String getAttachmentFilename(String articleId) {
        return "article-" + (articleId != null ? articleId.trim() : "documento") + "-jats.xml";
    }

    @Override
    public JatsPatternResponse getJatsForArticle(String articleId) throws IOException {
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("El identificador del artículo no puede estar vacío.");
        }
        String id = articleId.trim();

        Path xmlPath = findPatronXml(id);
        if (xmlPath == null) {
            throw new IllegalArgumentException(
                    "No se encontró ningún archivo XML patrón para el ID '" + id + "'. " +
                    "Asegúrese de que exista un archivo con el ID en su nombre dentro de archivosXMLPatron/.");
        }

        String xmlContent = Files.readString(xmlPath, StandardCharsets.UTF_8);

        // Normalizar financiamiento según SciELO SPS y PMC
        xmlContent = normalizeFunding(xmlContent);

        JatsValidator.ValidationResult validation = JatsValidator.validate(xmlContent);
        if (!validation.isValid()) {
            throw new IllegalStateException("El XML JATS no pasó la validación: " + validation.errors());
        }

        return new JatsPatternResponse(id, xmlContent, validation.isValid(), validation.errors());
    }

    private String normalizeFunding(String xml) {
        if (xml.contains("<funding-group>") && !xml.contains("<award-group")) {
            xml = xml.replaceAll("(?s)<funding-group>.*?</funding-group>\\s*", "");
        }

        if (!xml.contains("<award-group")) {
            xml = xml.replaceAll(
                "(<fn[^>]*?)fn-type=\"financial-disclosure\"",
                "$1fn-type=\"supported-by\""
            );
        }

        return xml;
    }

    private Path findPatronXml(String articleId) {
        for (String root : SEARCH_ROOTS) {
            Path dir = Paths.get(root);
            if (!Files.isDirectory(dir)) continue;

            try (Stream<Path> files = Files.list(dir)) {
                List<Path> matches = files
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".xml") && name.contains(articleId.toLowerCase());
                        })
                        .sorted((a, b) -> {
                            String na = a.getFileName().toString().toLowerCase();
                            String nb = b.getFileName().toString().toLowerCase();
                            boolean exactA = na.contains("-e" + articleId.toLowerCase() + ".xml")
                                          || na.contains("_e" + articleId.toLowerCase() + ".xml");
                            boolean exactB = nb.contains("-e" + articleId.toLowerCase() + ".xml")
                                          || nb.contains("_e" + articleId.toLowerCase() + ".xml");
                            if (exactA && !exactB) return -1;
                            if (!exactA && exactB) return 1;
                            return na.compareTo(nb);
                        })
                        .toList();

                if (!matches.isEmpty()) {
                    return matches.get(0);
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }
}
