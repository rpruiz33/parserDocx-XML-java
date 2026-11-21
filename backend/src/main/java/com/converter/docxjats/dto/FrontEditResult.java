package com.converter.docxjats.dto;

import java.util.List;

/**
 * DTO que encapsula el resultado de aplicar ediciones al front de un XML JATS,
 * incluyendo el estado de validación sintáctica.
 */
public record FrontEditResult(String xml, boolean valid, List<String> validationErrors) {}

