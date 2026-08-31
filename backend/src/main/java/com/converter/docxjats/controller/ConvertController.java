package com.converter.docxjats.controller;

import com.converter.docxjats.dto.ConversionResult;
import com.converter.docxjats.service.DocxToJatsConverter;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/convert")
public class ConvertController {

    private final DocxToJatsConverter converter;

    public ConvertController(DocxToJatsConverter converter) {
        this.converter = converter;
    }

    /**
     * Devuelve solo el XML (texto plano) para previsualizar en el navegador.
     * Si el documento tiene imágenes, sus referencias quedan como
     * "images/imgN.ext" pero los binarios no viajan en esta respuesta.
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(@RequestParam("file") MultipartFile file) {
        ValidationError error = validate(file);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error.message));
        }
        try {
            ConversionResult result = converter.convert(file.getInputStream(), file.getOriginalFilename());
            Map<String, Object> body = new HashMap<>();
            body.put("xml", result.getXml());
            body.put("imageCount", result.getImages().size());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo convertir el documento: " + e.getMessage()));
        }
    }

    /**
     * Devuelve el XML JATS (front + body + back) como archivo .xml descargable,
     * sin comprimir. Si el documento tiene imágenes, sus referencias quedan
     * como "images/imgN.ext" dentro del XML, pero los binarios no viajan en
     * esta respuesta (usar /package si se necesitan las imágenes también).
     */
    @PostMapping(value = "/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertToXml(@RequestParam("file") MultipartFile file) {
        ValidationError error = validate(file);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error.message));
        }
        try {
            ConversionResult result = converter.convert(file.getInputStream(), file.getOriginalFilename());

            byte[] xmlBytes = result.getXml().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String baseName = stripExtension(file.getOriginalFilename());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "xml", java.nio.charset.StandardCharsets.UTF_8));
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(baseName + "-jats.xml").build());

            return new ResponseEntity<>(xmlBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo convertir el documento: " + e.getMessage()));
        }
    }

    /**
     * Devuelve un .zip descargable con article.xml y la carpeta images/
     * con las imágenes embebidas encontradas en el .docx.
     * Se mantiene como alternativa para cuando el manuscrito tiene imágenes
     * y se necesitan los binarios junto con el XML.
     */
    @PostMapping(value = "/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertToPackage(@RequestParam("file") MultipartFile file) {
        ValidationError error = validate(file);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error.message));
        }
        try {
            ConversionResult result = converter.convert(file.getInputStream(), file.getOriginalFilename());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("article.xml"));
                zos.write(result.getXml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                for (Map.Entry<String, byte[]> img : result.getImages().entrySet()) {
                    zos.putNextEntry(new ZipEntry("images/" + img.getKey()));
                    zos.write(img.getValue());
                    zos.closeEntry();
                }
            }

            String baseName = stripExtension(file.getOriginalFilename());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(baseName + "-jats.zip").build());

            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo convertir el documento: " + e.getMessage()));
        }
    }

    private ValidationError validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ValidationError("No se recibió ningún archivo.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".docx")) {
            return new ValidationError("El archivo debe tener extensión .docx");
        }
        return null;
    }

    private String stripExtension(String filename) {
        if (filename == null) return "documento";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private record ValidationError(String message) {}
}
