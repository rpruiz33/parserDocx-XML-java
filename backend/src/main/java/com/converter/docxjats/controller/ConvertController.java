package com.converter.docxjats.controller;

import com.converter.docxjats.dto.ConversionPreviewDto;
import com.converter.docxjats.service.interfaces.IDocxService;
import com.converter.docxjats.service.interfaces.IDocumentoXmlService;
import com.converter.docxjats.service.interfaces.IPackageExportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controlador REST para conversión y empaquetado de documentos DOCX a JATS XML.
 * Persiste automáticamente en MySQL cada conversión realizada.
 */
@RestController
@RequestMapping("/api/convert")
public class ConvertController {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final IDocxService docxService;
    private final IPackageExportService packageExportService;
    private final IDocumentoXmlService documentoService;

    public ConvertController(IDocxService docxService,
                             IPackageExportService packageExportService,
                             IDocumentoXmlService documentoService) {
        this.docxService = docxService;
        this.packageExportService = packageExportService;
        this.documentoService = documentoService;
    }

    /**
     * Devuelve el XML y conteo de imágenes, y guarda automáticamente en la base de datos.
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file) {
        try {
            docxService.validateDocx(file != null ? file.getOriginalFilename() : null, file == null || file.isEmpty());
            ConversionPreviewDto preview = docxService.preview(file.getInputStream(), file.getOriginalFilename());

            // Persistencia automática en MySQL
            try {
                String originalName = file.getOriginalFilename();
                String codigo = extractArticleId(originalName);
                documentoService.guardar(originalName, codigo, preview.xml(), true);
            } catch (Exception ex) {
                // Se registra advertencia para no interrumpir la previsualización si la base de datos no está disponible
                System.err.println("[ConvertController] Advertencia: No se pudo auto-guardar en BD: " + ex.getMessage());
            }

            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo convertir el documento: " + e.getMessage()));
        }
    }

    private String extractArticleId(String filename) {
        if (filename == null) return "";
        Matcher m = NUMBER_PATTERN.matcher(filename);
        return m.find() ? m.group() : "";
    }

    /**
     * Devuelve el XML JATS como archivo .xml descargable.
     */
    @PostMapping(value = "/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertToXml(@RequestParam("file") MultipartFile file) {
        try {
            docxService.validateDocx(file != null ? file.getOriginalFilename() : null, file == null || file.isEmpty());
            byte[] xmlBytes = docxService.convertToXmlBytes(file.getInputStream(), file.getOriginalFilename());
            String filename = docxService.getXmlFilename(file.getOriginalFilename());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

            return new ResponseEntity<>(xmlBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo convertir el documento: " + e.getMessage()));
        }
    }

    /**
     * Devuelve un archivo .zip conteniendo article.xml y la subcarpeta images/.
     */
    @PostMapping(value = "/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertToPackage(@RequestParam("file") MultipartFile file) {
        try {
            docxService.validateDocx(file != null ? file.getOriginalFilename() : null, file == null || file.isEmpty());
            byte[] zipBytes = packageExportService.convertToZipPackage(file.getInputStream(), file.getOriginalFilename());
            String filename = packageExportService.getZipFilename(file.getOriginalFilename());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

            return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo convertir el documento: " + e.getMessage()));
        }
    }
}
