package com.converter.docxjats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Reporte de conformidad y aptitud técnica para PubMed Central (PMC Style Checker)
 * y SciELO (SciELO Publishing Schema SPS 1.9 / 1.10).
 */
public record ComplianceReportDto(
        boolean pmcApto,
        List<CheckItem> checksPmc,
        boolean scieloApto,
        List<CheckItem> checksScielo
) {
    // Compatibilidad retroactiva
    @JsonProperty("redalycApto")
    public boolean redalycApto() {
        return scieloApto;
    }

    @JsonProperty("checksRedalyc")
    public List<CheckItem> checksRedalyc() {
        return checksScielo;
    }

    public record CheckItem(
            String codigo,
            String nombre,
            boolean cumplido,
            String detalle
    ) {}
}
