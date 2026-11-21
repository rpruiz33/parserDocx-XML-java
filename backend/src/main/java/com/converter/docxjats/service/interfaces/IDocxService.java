package com.converter.docxjats.service.interfaces;

import com.converter.docxjats.dto.ConversionPreviewDto;
import java.io.IOException;
import java.io.InputStream;

/**
 * Interfaz unificada para el procesamiento de documentos DOCX:
 * agrupa validación de entrada y conversión a XML JATS / vista previa.
 *
 * <p>Reemplaza a {@code IDocxValidationService} e {@code IDocxConversionService}
 * que tenían cohesión natural al pertenecer al mismo caso de uso.
 */
public interface IDocxService {

    // ── Validación ────────────────────────────────────────────────────
    /** Lanza {@link IllegalArgumentException} si el archivo no es un .docx válido. */
    void validateDocx(String filename, boolean isEmpty);

    // ── Conversión ────────────────────────────────────────────────────
    /** Genera una vista previa del XML JATS sin descargarlo. */
    ConversionPreviewDto preview(InputStream inputStream, String originalFilename) throws IOException;

    /** Convierte el .docx a bytes UTF-8 del XML JATS resultante. */
    byte[] convertToXmlBytes(InputStream inputStream, String originalFilename) throws IOException;

    /** Devuelve el nombre del archivo .xml de descarga a partir del nombre original. */
    String getXmlFilename(String originalFilename);
}

