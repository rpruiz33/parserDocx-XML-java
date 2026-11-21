package com.converter.docxjats.service;

import com.converter.docxjats.dto.ConversionPreviewDto;
import com.converter.docxjats.dto.ConversionResult;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Servicio de negocio encargado de coordinar las conversiones de documentos DOCX,
 * validación de tipos de archivo, preparación de vistas previas y generación
 * de empaquetados binarios (archivos XML planos o paquetes ZIP con imágenes).
 */
@Service
public class ConversionService {

    private final DocxToJatsConverter converter;

    public ConversionService(DocxToJatsConverter converter) {
        this.converter = converter;
    }

    /**
     * Valida la presencia y extensión del archivo recibido.
     *
     * @param filename nombre original del archivo
     * @param isEmpty  indica si el archivo no contiene bytes
     * @throws IllegalArgumentException si el archivo es nulo, vacío o no tiene extensión .docx
     */
    public void validateDocx(String filename, boolean isEmpty) {
        if (isEmpty) {
            throw new IllegalArgumentException("No se recibió ningún archivo.");
        }
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("El archivo debe tener extensión .docx");
        }
    }

    /**
     * Genera la previsualización del documento DOCX en XML JATS.
     */
    public ConversionPreviewDto preview(InputStream inputStream, String originalFilename) throws IOException {
        ConversionResult result = converter.convert(inputStream, originalFilename);
        return new ConversionPreviewDto(result.getXml(), result.getImages().size());
    }

    /**
     * Convierte el documento DOCX a los bytes UTF-8 del archivo XML plano.
     */
    public byte[] convertToXmlBytes(InputStream inputStream, String originalFilename) throws IOException {
        ConversionResult result = converter.convert(inputStream, originalFilename);
        return result.getXml().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convierte el documento DOCX a un paquete ZIP conteniendo article.xml y la subcarpeta images/.
     */
    public byte[] convertToZipPackage(InputStream inputStream, String originalFilename) throws IOException {
        ConversionResult result = converter.convert(inputStream, originalFilename);
        return createZipPackage(result);
    }

    /**
     * Empaqueta el XML y sus imágenes embebidas en un archivo ZIP en memoria.
     */
    public byte[] createZipPackage(ConversionResult result) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("article.xml"));
            zos.write(result.getXml().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            for (Map.Entry<String, byte[]> img : result.getImages().entrySet()) {
                zos.putNextEntry(new ZipEntry("images/" + img.getKey()));
                zos.write(img.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /**
     * Construye el nombre canónico para el archivo XML descargable.
     */
    public String getXmlFilename(String originalFilename) {
        return stripExtension(originalFilename) + "-jats.xml";
    }

    /**
     * Construye el nombre canónico para el archivo ZIP descargable.
     */
    public String getZipFilename(String originalFilename) {
        return stripExtension(originalFilename) + "-jats.zip";
    }

    /**
     * Remueve la extensión de un archivo conservando su nombre base.
     */
    public String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) return "documento";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }
}

