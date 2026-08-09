package com.converter.docxjats.service.jats;

import java.util.ArrayList;
import java.util.List;

/**
 * Construye el bloque {@code <front>} del artículo JATS: metadatos de la
 * revista, título/subtítulo del artículo, autores (contrib-group) y el
 * resumen (abstract), si se detectó un encabezado "Resumen"/"Abstract" en el .docx.
 */
public class JatsFrontBuilder {

    /** Un autor detectado en la sección "Autores" del .docx (nombre + afiliación opcional). */
    private record Author(String name, String affiliation) {}

    private String title;
    private String subtitle;
    private final List<Author> authors = new ArrayList<>();
    private final StringBuilder abstractBody = new StringBuilder();

    public boolean hasTitle() {
        return title != null;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    /**
     * Agrega un autor detectado en la sección "Autores" del .docx.
     * {@code affiliation} puede ser null si no se especificó afiliación.
     */
    public void appendAuthor(String name, String affiliation) {
        if (name == null || name.isBlank()) return;
        authors.add(new Author(name.trim(), affiliation == null || affiliation.isBlank() ? null : affiliation.trim()));
    }

    public boolean hasAuthors() {
        return !authors.isEmpty();
    }

    /** Agrega un párrafo ya renderizado (con <bold>/<italic>/etc.) al resumen. */
    public void appendAbstractParagraph(String runsXml) {
        if (runsXml == null || runsXml.isBlank()) return;
        abstractBody.append("<p>").append(runsXml).append("</p>\n");
    }

    public boolean hasAbstract() {
        return abstractBody.length() > 0;
    }

    /**
     * Genera el XML del bloque front. Si no se detectó un título explícito
     * (estilo "Title"/"Título" en el .docx), se usa {@code fallbackTitle}
     * (por ejemplo, el nombre del archivo).
     * <p>
     * El orden de los elementos dentro de {@code <article-meta>} respeta el
     * modelo de contenido de JATS (Z39.96) v1.3: title-group, contrib-group,
     * abstract.
     */
    public String build(String fallbackTitle) {
        String finalTitle = hasTitle() ? title : fallbackTitle;

        StringBuilder sb = new StringBuilder();
        sb.append("<front>\n<journal-meta>\n")
          .append("<journal-id journal-id-type=\"publisher-id\">TBD</journal-id>\n")
          .append("<journal-title-group><journal-title>TBD</journal-title></journal-title-group>\n")
          .append("</journal-meta>\n");

        sb.append("<article-meta>\n");
        sb.append("<article-id pub-id-type=\"publisher-id\">TBD</article-id>\n");

        sb.append("<title-group>\n")
          .append("<article-title>").append(XmlUtils.escape(finalTitle)).append("</article-title>\n");
        if (subtitle != null && !subtitle.isBlank()) {
            sb.append("<subtitle>").append(XmlUtils.escape(subtitle)).append("</subtitle>\n");
        }
        sb.append("</title-group>\n");

        if (hasAuthors()) {
            sb.append("<contrib-group>\n");
            for (Author author : authors) {
                sb.append("<contrib contrib-type=\"author\">\n")
                  .append("<string-name>").append(XmlUtils.escape(author.name())).append("</string-name>\n");
                if (author.affiliation() != null) {
                    sb.append("<aff>").append(XmlUtils.escape(author.affiliation())).append("</aff>\n");
                }
                sb.append("</contrib>\n");
            }
            sb.append("</contrib-group>\n");
        }

        if (hasAbstract()) {
            sb.append("<abstract>\n").append(abstractBody).append("</abstract>\n");
        }

        sb.append("</article-meta>\n</front>\n");
        return sb.toString();
    }
}
