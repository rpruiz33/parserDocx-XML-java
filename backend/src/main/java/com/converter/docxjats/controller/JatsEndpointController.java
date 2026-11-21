package com.converter.docxjats.controller;

import com.converter.docxjats.dto.JatsPatternResponse;
import com.converter.docxjats.service.interfaces.IArticleIdResolver;
import com.converter.docxjats.service.interfaces.IJatsPatternProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controlador REST para consulta y descarga de artículos patrón JATS XML de la revista Salud Colectiva.
 * Cumple con el Principio de Segregación de Interfaces (ISP):
 * - {@link IArticleIdResolver}: resuelve y valida el ID solicitado.
 * - {@link IJatsPatternProvider}: recupera el archivo patrón normalizado y genera el nombre adjunto.
 */
@CrossOrigin(origins = "*")
@RestController
public class JatsEndpointController {

    private final IArticleIdResolver idResolver;
    private final IJatsPatternProvider patternProvider;

    public JatsEndpointController(IArticleIdResolver idResolver, IJatsPatternProvider patternProvider) {
        this.idResolver = idResolver;
        this.patternProvider = patternProvider;
    }

    @PostMapping(value = {"/jats", "/service/jats"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> getJatsXmlPost(@RequestBody(required = false) Map<String, String> payload,
                                            @RequestParam(required = false) String articleId) {
        String id;
        try {
            id = idResolver.resolveArticleId(articleId, payload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage()));
        }

        try {
            JatsPatternResponse response = patternProvider.getJatsForArticle(id);
            byte[] xmlBytes = response.xml().getBytes(StandardCharsets.UTF_8);
            String filename = patternProvider.getAttachmentFilename(id);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
            headers.setContentDisposition(
                    org.springframework.http.ContentDisposition.attachment().filename(filename).build());

            return new ResponseEntity<>(xmlBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Error generando o validando JATS XML: " + e.getMessage()));
        }
    }

    @GetMapping(value = {"/jats/{articleId}", "/service/jats/{articleId}"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> getJatsXmlGet(@PathVariable String articleId) {
        return getJatsXmlPost(null, articleId);
    }
}
