package com.converter.docxjats.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resultado de convertir un .docx a JATS: el XML generado
 * y las imágenes embebidas encontradas (nombre de archivo -> bytes).
 */
public class ConversionResult {

    private final String xml;
    private final Map<String, byte[]> images = new LinkedHashMap<>();
    private int headingWarnings = 0;

    public ConversionResult(String xml) {
        this.xml = xml;
    }

    public String getXml() {
        return xml;
    }

    public Map<String, byte[]> getImages() {
        return images;
    }

    public void addImage(String filename, byte[] data) {
        images.put(filename, data);
    }

    public int getHeadingWarnings() {
        return headingWarnings;
    }

    public void setHeadingWarnings(int headingWarnings) {
        this.headingWarnings = Math.max(headingWarnings, 0);
    }
}
