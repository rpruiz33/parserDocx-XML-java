package com.converter.docxjats.service.jats;

import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registra las imágenes embebidas encontradas en el .docx y les asigna
 * un nombre de archivo único (img1.png, img2.jpg, ...) para referenciarlas
 * desde el XML JATS vía xlink:href="images/imgN.ext".
 */
public class ImageRegistry {

    private final Map<String, byte[]> images = new LinkedHashMap<>();
    private final Map<String, String> alreadyRegistered = new HashMap<>();
    private int counter = 0;

    /** Registra la imagen (si no estaba ya) y devuelve el nombre de archivo asignado, o null si falla. */
    public String register(XWPFPicture picture) {
        try {
            XWPFPictureData data = picture.getPictureData();
            if (data == null) return null;
            String key = data.getFileName();
            if (key != null && alreadyRegistered.containsKey(key)) {
                return alreadyRegistered.get(key);
            }
            counter++;
            String ext = data.suggestFileExtension();
            if (ext == null || ext.isBlank()) ext = "png";
            String filename = "img" + counter + "." + ext;
            images.put(filename, data.getData());
            if (key != null) {
                alreadyRegistered.put(key, filename);
            }
            return filename;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, byte[]> getImages() {
        return images;
    }
}
