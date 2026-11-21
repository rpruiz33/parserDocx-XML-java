package com.converter.docxjats.service.interfaces;

import java.util.Map;

/**
 * Interfaz segregada para la resolución de identificadores de artículos a partir de solicitudes HTTP.
 */
public interface IArticleIdResolver {
    String resolveArticleId(String articleId, Map<String, String> payload);
}

