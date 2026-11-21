package com.converter.docxjats.controller;

import com.converter.docxjats.dto.FrontEditResult;
import com.converter.docxjats.dto.FrontFields;
import com.converter.docxjats.service.FrontEditService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controlador REST para la edición rápida de campos del &lt;front&gt; sobre un XML JATS ya generado.
 * Responsable únicamente del transporte HTTP. Toda la manipulación del DOM XML y revalidación
 * reside en {@link FrontEditService}.
 */
@RestController
@RequestMapping("/api/convert")
public class FrontEditController {

    private final FrontEditService frontEditService;

    public FrontEditController(FrontEditService frontEditService) {
        this.frontEditService = frontEditService;
    }

    /**
     * Extrae del XML los campos editables del front para precargar el formulario.
     */
    @PostMapping(value = "/front-fields", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> frontFields(@RequestBody Map<String, String> body) {
        String xml = body != null ? body.get("xml") : null;
        try {
            FrontFields fields = frontEditService.extractFields(xml);
            return ResponseEntity.ok(fields);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudieron leer los campos del front: " + e.getMessage()));
        }
    }

    /**
     * Aplica los campos editados sobre el XML y devuelve el XML actualizado con el resultado de validación.
     */
    @PostMapping(value = "/apply-front", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> applyFront(@RequestBody ApplyFrontRequest request) {
        String xml = request != null ? request.xml() : null;
        FrontFields fields = request != null ? request.front() : null;
        try {
            FrontEditResult result = frontEditService.applyFront(xml, fields);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo aplicar la edición: " + e.getMessage()));
        }
    }

    /**
     * Descarga como .xml un XML ya editado.
     */
    @PostMapping(value = "/export-xml", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exportXml(@RequestBody Map<String, String> body) {
        String xml = body != null ? body.get("xml") : null;
        String filename = body != null ? body.get("filename") : null;
        try {
            byte[] xmlBytes = frontEditService.prepareXmlExport(xml);
            String exportFilename = frontEditService.sanitizeExportFilename(filename);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
            headers.setContentDisposition(ContentDisposition.attachment().filename(exportFilename).build());

            return new ResponseEntity<>(xmlBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo exportar el XML: " + e.getMessage()));
        }
    }

    public record ApplyFrontRequest(String xml, FrontFields front) {}
}
