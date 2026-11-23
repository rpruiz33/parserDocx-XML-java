package com.converter.docxjats.service.interfaces;

import com.converter.docxjats.dto.ComplianceReportDto;

/**
 * Interfaz segregada para auditar la conformidad de documentos XML JATS
 * frente a los requisitos técnicos de PubMed Central (PMC) y Redalyc (Marcalyc / JATS4R).
 */
public interface IComplianceValidationService {

    /**
     * Evalúa el XML JATS y devuelve un informe detallado de aptitud para PMC y Redalyc.
     *
     * @param xmlContent Documento XML JATS completo en formato texto
     * @return {@link ComplianceReportDto} con los veredictos y el detalle de cada regla
     */
    ComplianceReportDto auditCompliance(String xmlContent);
}

