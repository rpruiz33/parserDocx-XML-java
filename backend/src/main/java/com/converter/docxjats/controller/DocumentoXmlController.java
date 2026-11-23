package com.converter.docxjats.controller;

import com.converter.docxjats.dto.DocumentoXmlResumenDto;
import com.converter.docxjats.entity.DocumentoXml;
import com.converter.docxjats.service.interfaces.IDocumentoXmlService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para interactuar con la base de datos de documentos XML JATS.
 *
 * <p>El endpoint {@code GET /api/documentos} devuelve solo metadatos (sin el XML completo)
 * para evitar respuestas JSON masivas. Para cargar el contenido completo de un documento,
 * usar {@code GET /api/documentos/{id}}.</p>
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
            // Devolver resumen (sin contenidoXml) para no hinchar la respuesta
            return ResponseEntity.status(HttpStatus.CREATED).body(new DocumentoXmlResumenDto(
                    guardado.getId(),
                    guardado.getNombreArchivo(),
                    guardado.getCodigoArticulo(),
                    guardado.isEsValido(),
                    guardado.getFechaCreacion()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar en base de datos: " + e.getMessage()));
        }
    }

    /**
     * Lista todos los documentos como DTOs de resumen (SIN el campo contenidoXml).
     * Para obtener el XML completo de un documento, usar GET /api/documentos/{id}.
     */
    @GetMapping
    public ResponseEntity<List<DocumentoXmlResumenDto>> listar() {
        return ResponseEntity.ok(documentoService.listarResumenes());
    }

    /**
     * Obtiene un documento completo (con contenidoXml) por su ID.
     * Este es el endpoint a usar cuando el usuario hace clic en "Cargar".
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

