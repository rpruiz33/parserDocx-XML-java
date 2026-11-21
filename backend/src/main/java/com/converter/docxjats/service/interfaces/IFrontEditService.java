package com.converter.docxjats.service.interfaces;

import com.converter.docxjats.dto.FrontEditResult;
import com.converter.docxjats.dto.FrontFields;

/**
 * Interfaz unificada para la edición de metadatos del front JATS:
 * agrupa extracción de campos, edición in-place y exportación del XML resultante.
 *
 * <p>Reemplaza a {@code IFrontFieldsExtractor}, {@code IFrontEditorService}
 * e {@code IXmlExportService}, que forman parte del mismo caso de uso.
 */
public interface IFrontEditService {

    // ── Extracción ────────────────────────────────────────────────────
    /** Lee y extrae los campos estructurados del bloque {@code <front>} del XML dado. */
    FrontFields extractFields(String xml) throws Exception;

    // ── Edición ───────────────────────────────────────────────────────
    /** Aplica los {@link FrontFields} sobre el XML JATS y revalida la estructura resultante. */
    FrontEditResult applyFront(String xml, FrontFields fields) throws Exception;

    // ── Exportación ───────────────────────────────────────────────────
    /** Convierte el XML a bytes UTF-8 listos para descarga. */
    byte[] prepareXmlExport(String xml);

    /** Genera un nombre de archivo sanitizado para la descarga del XML editado. */
    String sanitizeExportFilename(String filename);
}

