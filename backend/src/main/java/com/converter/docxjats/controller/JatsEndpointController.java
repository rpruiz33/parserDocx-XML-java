package com.converter.docxjats.controller;

import com.converter.docxjats.service.JatsPatternService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class JatsEndpointController {

    private final JatsPatternService jatsPatternService;

    public JatsEndpointController(JatsPatternService jatsPatternService) {
        this.jatsPatternService = jatsPatternService;
    }

    @PostMapping(value = {"/jats", "/service/jats"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> getJatsXmlPost(@RequestBody(required = false) Map<String, String> payload,
                                          @RequestParam(required = false) String articleId) {
        String id = articleId;
        if ((id == null || id.isBlank()) && payload != null) {
            id = payload.get("articleId");
            if (id == null) {
                id = payload.get("id");
            }
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Debe proporcionar el identificador del artículo (ej. '6032' o '5939') en el parámetro 'articleId' o en el payload JSON."));
        }

        try {
            JatsPatternService.JatsResponse response = jatsPatternService.getJatsForArticle(id);
            byte[] xmlBytes = response.xml().getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setContentDisposition(
                    org.springframework.http.ContentDisposition.attachment().filename("article-" + id + "-jats.xml").build());

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
