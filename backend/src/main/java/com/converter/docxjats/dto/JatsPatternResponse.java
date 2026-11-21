package com.converter.docxjats.dto;

import java.util.List;

/**
 * DTO que representa la respuesta al recuperar un XML JATS patrón normalizado y validado.
 */
public record JatsPatternResponse(String articleId, String xml, boolean isValid, List<String> validationErrors) {}

