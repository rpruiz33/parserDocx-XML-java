package com.converter.docxjats.service;

import com.converter.docxjats.service.jats.JatsValidator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class JatsPatternService {

    public JatsResponse getJatsForArticle(String articleId) throws IOException {
        String fileName;
        if ("6032".equals(articleId)) {
            fileName = "1851-8265-scol-22-e6032.xml";
        } else if ("5939".equals(articleId)) {
            fileName = "1851-8265-scol-22-e5939.xml";
        } else {
            throw new IllegalArgumentException("Artículo no encontrado o patrón no soportado: " + articleId);
        }

        Path xmlPath = Paths.get("../archivosXMLPatron", fileName);
        if (!Files.exists(xmlPath)) {
            xmlPath = Paths.get("archivosXMLPatron", fileName);
        }
        if (!Files.exists(xmlPath)) {
            // Try absolute path from workspace root
            xmlPath = Paths.get("/home/beto/Desktop/practica PP/parserDocx-XML-java/archivosXMLPatron", fileName);
        }
        if (!Files.exists(xmlPath)) {
            throw new IOException("No se encontró el archivo XML patrón para el ID " + articleId);
        }

        String xmlContent = Files.readString(xmlPath, StandardCharsets.UTF_8);

        JatsValidator.ValidationResult validation = JatsValidator.validate(xmlContent);
        if (!validation.isValid()) {
            throw new IllegalStateException("El XML JATS no pasó la validación: " + validation.errors());
        }

        return new JatsResponse(articleId, xmlContent, validation.isValid(), validation.errors());
    }

    public record JatsResponse(String articleId, String xml, boolean isValid, List<String> validationErrors) {}
}
