package com.converter.docxjats.service.interfaces;

import com.converter.docxjats.dto.ConversionResult;
import java.io.IOException;
import java.io.InputStream;

/**
 * Interfaz segregada para la creación y exportación de paquetes ZIP con imágenes.
 */
public interface IPackageExportService {
    byte[] convertToZipPackage(InputStream inputStream, String originalFilename) throws IOException;
    byte[] createZipPackage(ConversionResult result) throws IOException;
    String getZipFilename(String originalFilename);
}

