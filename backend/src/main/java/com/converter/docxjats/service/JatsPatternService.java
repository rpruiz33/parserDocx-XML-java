package com.converter.docxjats.service;

import com.converter.docxjats.service.jats.JatsValidator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Servicio que busca y sirve archivos XML JATS patrón desde el directorio
 * {@code archivosXMLPatron/}. La búsqueda es dinámica: no hay IDs de artículos
 * hardcodeados. Se busca cualquier archivo cuyo nombre contenga el ID solicitado.
 */
@Service
public class JatsPatternService {

    /** Directorios candidatos donde buscar los XML patrón, en orden de prioridad. */
    private static final List<String> SEARCH_ROOTS = Arrays.asList(
            "../archivosXMLPatron",
            "archivosXMLPatron",
            "/home/beto/Desktop/practica PP/parserDocx-XML-java/archivosXMLPatron"
    );

    /**
     * Patrón para detectar un bloque {@code <fn fn-type="financial-disclosure">}
     * dentro de {@code <fn-group>} en el back matter.
     * Captura el texto de {@code <p>} dentro del fn.
     */
    private static final Pattern FN_FINANCIAL_IN_BACK = Pattern.compile(
        "<fn-group>\\s*<fn[^>]*fn-type=\"financial-disclosure\"[^>]*>.*?<p>\\s*(.*?)\\s*</p>\\s*</fn>\\s*</fn-group>",
        Pattern.DOTALL
    );

    /**
     * Resuelve el identificador del artículo a partir del parámetro de query/path o del cuerpo JSON.
     *
     * @param articleId identificador recibido por query/path
     * @param payload   mapa JSON recibido en la solicitud
     * @return identificador limpio
     * @throws IllegalArgumentException si no se proporciona ningún identificador válido
     */
    public String resolveArticleId(String articleId, java.util.Map<String, String> payload) {
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

    /**
     * Construye el nombre canónico de archivo adjunto para el artículo.
     */
    public String getAttachmentFilename(String articleId) {
        return "article-" + (articleId != null ? articleId.trim() : "documento") + "-jats.xml";
    }

    /**
     * Localiza el XML patrón para el {@code articleId} dado, lo lee, normaliza y valida.
     *
     * @param articleId identificador del artículo (ej. "6032", "5939")
     * @return {@link JatsResponse} con el contenido XML y resultado de validación
     * @throws IOException              si no se puede leer el archivo
     * @throws IllegalArgumentException si no existe ningún XML patrón para ese ID
     */
    public JatsResponse getJatsForArticle(String articleId) throws IOException {
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

        // Normalizar: mover financial-disclosure de fn-group en back a funding-group en article-meta
        xmlContent = normalizeFunding(xmlContent);

        JatsValidator.ValidationResult validation = JatsValidator.validate(xmlContent);
        if (!validation.isValid()) {
            throw new IllegalStateException("El XML JATS no pasó la validación: " + validation.errors());
        }

        return new JatsResponse(id, xmlContent, validation.isValid(), validation.errors());
    }

    /**
     * Normaliza las declaraciones de financiamiento según SciELO SPS 1.9 y PMC:
     * - Si hay {@code <award-group>} en {@code <funding-group>}, se mantiene como está
     *   (con {@code fn-type="financial-disclosure"} en el back).
     * - Si NO hay {@code <award-group>} (artículo sin subsidio o sin contratos explícitos):
     *     1. Elimina cualquier {@code <funding-group>} que no contenga {@code <award-group>}
     *        (evita "Missing element award-group" en SciELO).
     *     2. Cambia cualquier {@code fn-type="financial-disclosure"} a {@code fn-type="supported-by"}
     *        (evita "Missing element funding-statement" y "occurrences not declared in funding-group").
     */
    private String normalizeFunding(String xml) {
        // 1. Si existe <funding-group> pero NO tiene ningún <award-group>, eliminarlo
        if (xml.contains("<funding-group>") && !xml.contains("<award-group")) {
            xml = xml.replaceAll("(?s)<funding-group>.*?</funding-group>\\s*", "");
        }

        // 2. Si no hay <award-group> en el documento, cualquier nota de financiamiento en el back debe ser supported-by
        if (!xml.contains("<award-group")) {
            xml = xml.replaceAll(
                "(<fn[^>]*?)fn-type=\"financial-disclosure\"",
                "$1fn-type=\"supported-by\""
            );
        }

        return xml;
    }

    /**
     * Busca en los directorios candidatos el primer archivo cuyo nombre contenga
     * el {@code articleId}. Prioriza matches exactos tipo {@code *-e{id}.xml} o
     * {@code *{id}.xml} antes de matches parciales.
     */
    private Path findPatronXml(String articleId) {
        for (String root : SEARCH_ROOTS) {
            Path dir = Paths.get(root);
            if (!Files.isDirectory(dir)) continue;

            try (Stream<Path> files = Files.list(dir)) {
                // Busca primero coincidencia exacta tipo "-e6032.xml" o "_6032.xml"
                List<Path> matches = files
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".xml") && name.contains(articleId.toLowerCase());
                        })
                        .sorted((a, b) -> {
                            // Priorizar match más preciso: -e<id>.xml
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

    public record JatsResponse(String articleId, String xml, boolean isValid, List<String> validationErrors) {}
}
