package com.converter.docxjats.controller;

import com.converter.docxjats.dto.ConversionPreviewDto;
import com.converter.docxjats.service.interfaces.IDocxService;
import com.converter.docxjats.service.interfaces.IPackageExportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controlador REST para conversión y empaquetado de documentos DOCX a JATS XML.
 * Depende de {@link IDocxService} (validación + conversión unificada)
 * e {@link IPackageExportService} (generación de paquetes ZIP con imágenes).
 */
@RestController
@RequestMapping("/api/convert")
public class ConvertController {

    private final IDocxService docxService;
    private final IPackageExportService packageExportService;

    public ConvertController(IDocxService docxService,
                             IPackageExportService packageExportService) {
        this.docxService = docxService;
        this.packageExportService = packageExportService;
    }

    /**
     * Devuelve solo el XML y conteo de imágenes para previsualizar en el frontend.
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file) {
        try {
            docxService.validateDocx(file != null ? file.getOriginalFilename() : null, file == null || file.isEmpty());
            ConversionPreviewDto preview = docxService.preview(file.getInputStream(), file.getOriginalFilename());
            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo convertir el documento: " + e.getMessage()));
        }
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
