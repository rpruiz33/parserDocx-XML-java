package com.converter.docxjats.service;

import com.converter.docxjats.dto.FrontEditResult;
import com.converter.docxjats.dto.FrontFields;
import com.converter.docxjats.service.jats.JatsFrontEditor;
import com.converter.docxjats.service.jats.JatsValidator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Servicio de negocio encargado de la edición in-place de metadatos del &lt;front&gt;,
 * extracción de campos para formularios y validación sintáctica tras modificaciones.
 */
@Service
public class FrontEditService {

    /**
     * Extrae los campos editables del front a partir del XML plano.
     */
    public FrontFields extractFields(String xml) throws Exception {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Debe enviar el XML a analizar en el campo 'xml'.");
        }
        return JatsFrontEditor.extract(xml);
    }

    /**
     * Aplica las modificaciones de metadatos sobre el DOM XML y revalida el documento resultante.
     */
    public FrontEditResult applyFront(String xml, FrontFields fields) throws Exception {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Debe enviar el XML original en el campo 'xml'.");
        }
        FrontFields safeFields = fields != null ? fields : new FrontFields();
        String updatedXml = JatsFrontEditor.applyFront(xml, safeFields);
        JatsValidator.ValidationResult validation = JatsValidator.validate(updatedXml);
        return new FrontEditResult(updatedXml, validation.isValid(), validation.errors());
    }

    /**
     * Convierte el XML editado a bytes UTF-8 para su exportación.
     */
    public byte[] prepareXmlExport(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Debe enviar el XML a descargar en el campo 'xml'.");
        }
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Sanitiza el nombre de archivo de exportación asegurando sufijo canónico.
     */
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
