package com.converter.docxjats.controller;

import com.converter.docxjats.entity.DocumentoXml;
import com.converter.docxjats.service.interfaces.IDocumentoXmlService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para interactuar con la base de datos MySQL de documentos XML JATS.
 */
@RestController
@RequestMapping("/api/documentos")
@CrossOrigin(origins = "*")
public class DocumentoXmlController {

    private final IDocumentoXmlService documentoService;

    public DocumentoXmlController(IDocumentoXmlService documentoService) {
        this.documentoService = documentoService;
    }

    /**
     * Guarda un XML en la base de datos.
     * Payload JSON: { "nombreArchivo": "articulo-6032.docx", "codigoArticulo": "6032", "xml": "...", "esValido": true }
     */
    @PostMapping
    public ResponseEntity<?> guardar(@RequestBody Map<String, Object> body) {
        try {
            String xml = (String) body.get("xml");
            String nombreArchivo = (String) body.getOrDefault("nombreArchivo", "documento.xml");
            String codigoArticulo = (String) body.get("codigoArticulo");
            boolean esValido = Boolean.TRUE.equals(body.getOrDefault("esValido", true));

            DocumentoXml guardado = documentoService.guardar(nombreArchivo, codigoArticulo, xml, esValido);
            return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar en base de datos: " + e.getMessage()));
        }
    }

    /**
     * Lista todos los documentos almacenados en la base de datos.
     */
    @GetMapping
    public ResponseEntity<List<DocumentoXml>> listar() {
        return ResponseEntity.ok(documentoService.listarTodos());
    }

    /**
     * Obtiene un documento por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable("id") Long id) {
        return documentoService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Descarga el XML de un documento guardado como archivo adjunto.
     */
    @GetMapping("/{id}/xml")
    public ResponseEntity<?> descargarXml(@PathVariable("id") Long id) {
        return documentoService.obtenerPorId(id)
                .map(doc -> {
                    byte[] bytes = doc.getContenidoXml().getBytes(StandardCharsets.UTF_8);
                    String filename = doc.getNombreArchivo();
                    if (!filename.toLowerCase().endsWith(".xml")) {
                        filename = filename.replaceAll("(?i)\\.docx$", "") + ".xml";
                    }

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
                    headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

                    return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Elimina un documento por su ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable("id") Long id) {
        boolean eliminado = documentoService.eliminarPorId(id);
        if (eliminado) {
            return ResponseEntity.ok(Map.of("mensaje", "Documento eliminado correctamente."));
        }
        return ResponseEntity.notFound().build();
    }
}

