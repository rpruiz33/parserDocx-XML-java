package com.converter.docxjats.service.impl;

import com.converter.docxjats.dto.ConversionPreviewDto;
import com.converter.docxjats.dto.ConversionResult;
import com.converter.docxjats.service.interfaces.IDocxService;
import com.converter.docxjats.service.interfaces.IJatsDocumentConverter;
import com.converter.docxjats.service.interfaces.IPackageExportService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Implementación unificada de validación, conversión y empaquetado de documentos DOCX.
 * Implementa {@link IDocxService} (validación + conversión) e {@link IPackageExportService}.
 */
@Service
public class ConversionServiceImpl implements IDocxService, IPackageExportService {

    private final IJatsDocumentConverter converter;

    public ConversionServiceImpl(IJatsDocumentConverter converter) {
        this.converter = converter;
    }

    // --- IDocxValidationService ---
    @Override
    public void validateDocx(String filename, boolean isEmpty) {
        if (isEmpty) {
            throw new IllegalArgumentException("No se recibió ningún archivo.");
        }
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("El archivo debe tener extensión .docx");
        }
    }

    // --- IDocxConversionService ---
    @Override
    public ConversionPreviewDto preview(InputStream inputStream, String originalFilename) throws IOException {
        ConversionResult result = converter.convert(inputStream, originalFilename);
        return new ConversionPreviewDto(result.getXml(), result.getImages().size());
    }

    @Override
    public byte[] convertToXmlBytes(InputStream inputStream, String originalFilename) throws IOException {
        ConversionResult result = converter.convert(inputStream, originalFilename);
        return result.getXml().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getXmlFilename(String originalFilename) {
        return stripExtension(originalFilename) + "-jats.xml";
    }

    // --- IPackageExportService ---
    @Override
    public byte[] convertToZipPackage(InputStream inputStream, String originalFilename) throws IOException {
        ConversionResult result = converter.convert(inputStream, originalFilename);
        return createZipPackage(result);
    }

    @Override
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

    @Override
    public String getZipFilename(String originalFilename) {
        return stripExtension(originalFilename) + "-jats.zip";
    }

    private String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) return "documento";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }
}
