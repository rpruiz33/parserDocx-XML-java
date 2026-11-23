package com.converter.docxjats.dto;

import java.util.List;

/**
 * Reporte de conformidad y aptitud técnica para PubMed Central (PMC) y Redalyc (Marcalyc / JATS4R).
 */
public record ComplianceReportDto(
        boolean pmcApto,
        List<CheckItem> checksPmc,
        boolean redalycApto,
        List<CheckItem> checksRedalyc
) {
    public record CheckItem(
            String codigo,
            String nombre,
            boolean cumplido,
            String detalle
    ) {}
}

