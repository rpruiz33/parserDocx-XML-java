package com.converter.docxjats.controller;

import com.converter.docxjats.dto.FrontFields;
import com.converter.docxjats.service.jats.JatsFrontEditor;
import com.converter.docxjats.service.jats.JatsValidator;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Edición rápida de campos del &lt;front&gt; sobre un XML JATS ya generado
 * (título, subtítulo, resumen, palabras clave, autores), sin volver a subir
 * el .docx original.
 */
@RestController
@RequestMapping("/api/convert")
public class FrontEditController {

    /**
     * Extrae del XML los campos editables del front para precargar un formulario.
     */
    @PostMapping(value = "/front-fields", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> frontFields(@RequestBody Map<String, String> body) {
        String xml = body != null ? body.get("xml") : null;
        if (xml == null || xml.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debe enviar el XML a analizar en el campo 'xml'."));
        }
        try {
            FrontFields fields = JatsFrontEditor.extract(xml);
            return ResponseEntity.ok(fields);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudieron leer los campos del front: " + e.getMessage()));
        }
    }

    /**
     * Aplica los campos editados sobre el XML y devuelve el XML actualizado,
     * junto con el resultado de re-validarlo.
     */
    @PostMapping(value = "/apply-front", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> applyFront(@RequestBody ApplyFrontRequest request) {
        if (request == null || request.xml() == null || request.xml().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debe enviar el XML original en el campo 'xml'."));
        }
        FrontFields fields = request.front() != null ? request.front() : new FrontFields();
        try {
            String updatedXml = JatsFrontEditor.applyFront(request.xml(), fields);
            JatsValidator.ValidationResult validation = JatsValidator.validate(updatedXml);
            return ResponseEntity.ok(Map.of(
                    "xml", updatedXml,
                    "valid", validation.isValid(),
                    "validationErrors", validation.errors()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo aplicar la edición: " + e.getMessage()));
        }
    }

    /**
     * Descarga como .xml un XML ya editado (por ejemplo, el resultado de /apply-front),
     * sin necesidad de volver a subir el .docx. No incluye imágenes: para eso usar
     * /api/convert/package con el archivo original.
     */
    @PostMapping(value = "/export-xml", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exportXml(@RequestBody Map<String, String> body) {
        String xml = body != null ? body.get("xml") : null;
        if (xml == null || xml.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debe enviar el XML a descargar en el campo 'xml'."));
        }
        String filename = body.getOrDefault("filename", "documento");
        if (filename.isBlank()) filename = "documento";

        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename + "-jats-editado.xml").build());
        return new ResponseEntity<>(xmlBytes, headers, HttpStatus.OK);
    }

    public record ApplyFrontRequest(String xml, FrontFields front) {}
}
