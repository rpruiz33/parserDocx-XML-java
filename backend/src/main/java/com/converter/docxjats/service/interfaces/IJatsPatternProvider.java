package com.converter.docxjats.service.interfaces;

import com.converter.docxjats.dto.JatsPatternResponse;
import java.io.IOException;

/**
 * Interfaz segregada para la recuperación de artículos patrón certificados y nombres de descarga.
 */
public interface IJatsPatternProvider {
    JatsPatternResponse getJatsForArticle(String articleId) throws IOException;
    String getAttachmentFilename(String articleId);
}

