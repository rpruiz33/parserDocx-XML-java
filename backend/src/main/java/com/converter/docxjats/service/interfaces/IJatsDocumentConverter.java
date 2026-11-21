package com.converter.docxjats.service.interfaces;

import com.converter.docxjats.dto.ConversionResult;
import java.io.IOException;
import java.io.InputStream;

/**
 * Interfaz para el motor central de conversión de archivos DOCX al modelo de objetos JATS XML.
 */
public interface IJatsDocumentConverter {
    ConversionResult convert(InputStream docxStream, String originalFilename) throws IOException;
}

