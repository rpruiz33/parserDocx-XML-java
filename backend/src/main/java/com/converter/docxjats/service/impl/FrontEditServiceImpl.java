package com.converter.docxjats.service.impl;

import com.converter.docxjats.dto.FrontEditResult;
import com.converter.docxjats.dto.FrontFields;
import com.converter.docxjats.service.interfaces.IFrontEditService;
import com.converter.docxjats.service.jats.JatsFrontEditor;
import com.converter.docxjats.service.jats.JatsValidator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Implementación unificada de extracción, edición in-place y exportación de metadatos del front.
 * Implementa {@link IFrontEditService} que agrupa las tres responsabilidades del caso de uso.
 */
@Service
public class FrontEditServiceImpl implements IFrontEditService {

    // --- IFrontFieldsExtractor ---
    @Override
    public FrontFields extractFields(String xml) throws Exception {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Debe enviar el XML a analizar en el campo 'xml'.");
        }
        return JatsFrontEditor.extractStatic(xml);
    }

    // --- IFrontEditorService ---
    @Override
    public FrontEditResult applyFront(String xml, FrontFields fields) throws Exception {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Debe enviar el XML original en el campo 'xml'.");
        }
        FrontFields safeFields = fields != null ? fields : new FrontFields();
        String updatedXml = JatsFrontEditor.applyFrontStatic(xml, safeFields);
        JatsValidator.ValidationResult validation = JatsValidator.validate(updatedXml);
        return new FrontEditResult(updatedXml, validation.isValid(), validation.errors());
    }

    // --- IXmlExportService ---
    @Override
    public byte[] prepareXmlExport(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Debe enviar el XML a descargar en el campo 'xml'.");
        }
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String sanitizeExportFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "documento-jats-editado.xml";
        }
        String clean = filename.trim();
        if (clean.toLowerCase().endsWith(".xml")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        return clean + "-jats-editado.xml";
    }
}
