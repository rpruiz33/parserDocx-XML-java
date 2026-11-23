package com.converter.docxjats.controller;

import com.converter.docxjats.dto.ComplianceReportDto;
import com.converter.docxjats.service.interfaces.IComplianceValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para auditar la conformidad de documentos XML JATS
 * frente a los estándares de PubMed Central (PMC) y Redalyc (Marcalyc / JATS4R).
 */
@RestController
@RequestMapping("/api/compliance")
@CrossOrigin(origins = "*")
public class ComplianceController {

    private final IComplianceValidationService complianceService;

    public ComplianceController(IComplianceValidationService complianceService) {
        this.complianceService = complianceService;
    }

    /**
     * Evalúa el XML recibido y devuelve un reporte detallado con veredictos para PMC y Redalyc.
     * Payload: { "xml": "<?xml ...>" }
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> audit(@RequestBody Map<String, String> body) {
        String xml = body != null ? body.get("xml") : null;
        if (xml == null || xml.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debe proporcionar el XML a auditar en el campo 'xml'."));
        }
        try {
            ComplianceReportDto report = complianceService.auditCompliance(xml);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error durante la auditoría de conformidad: " + e.getMessage()));
        }
    }
}

