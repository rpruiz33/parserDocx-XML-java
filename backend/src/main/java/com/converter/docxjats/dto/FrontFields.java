package com.converter.docxjats.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Subconjunto "editable rápido" de los metadatos del &lt;front&gt; JATS:
 * título/subtítulo, resumen (es/en), palabras clave (es/en) y autores
 * (nombre, ORCID, email de correspondencia si ya era autor de correspondencia).
 *
 * No cubre journal-meta, financiamiento, licencias ni historial editorial:
 * eso se sigue resolviendo con la conversión original desde el .docx.
 */
public class FrontFields {

    private String articleTitle = "";
    private String subtitle = "";
    private String transTitle = "";
    private String abstractText = "";
    private String transAbstractText = "";
    private List<String> keywordsEs = new ArrayList<>();
    private List<String> keywordsEn = new ArrayList<>();
    private List<AuthorField> authors = new ArrayList<>();

    public String getArticleTitle() {
        return articleTitle;
    }

    public void setArticleTitle(String articleTitle) {
        this.articleTitle = articleTitle;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getTransTitle() {
        return transTitle;
    }

    public void setTransTitle(String transTitle) {
        this.transTitle = transTitle;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getTransAbstractText() {
        return transAbstractText;
    }

    public void setTransAbstractText(String transAbstractText) {
        this.transAbstractText = transAbstractText;
    }

    public List<String> getKeywordsEs() {
        return keywordsEs;
    }

    public void setKeywordsEs(List<String> keywordsEs) {
        this.keywordsEs = keywordsEs != null ? keywordsEs : new ArrayList<>();
    }

    public List<String> getKeywordsEn() {
        return keywordsEn;
    }

    public void setKeywordsEn(List<String> keywordsEn) {
        this.keywordsEn = keywordsEn != null ? keywordsEn : new ArrayList<>();
    }

    public List<AuthorField> getAuthors() {
        return authors;
    }

    public void setAuthors(List<AuthorField> authors) {
        this.authors = authors != null ? authors : new ArrayList<>();
    }

    public static class AuthorField {
        private String givenNames = "";
        private String surname = "";
        private String orcid = "";
        private boolean corresponding = false;
        private String correspEmail = "";

        public String getGivenNames() {
            return givenNames;
        }

        public void setGivenNames(String givenNames) {
            this.givenNames = givenNames;
        }

        public String getSurname() {
            return surname;
        }

        public void setSurname(String surname) {
            this.surname = surname;
        }

        public String getOrcid() {
            return orcid;
        }

        public void setOrcid(String orcid) {
            this.orcid = orcid;
        }

        public boolean isCorresponding() {
            return corresponding;
        }

        public void setCorresponding(boolean corresponding) {
            this.corresponding = corresponding;
        }

        public String getCorrespEmail() {
            return correspEmail;
        }

        public void setCorrespEmail(String correspEmail) {
            this.correspEmail = correspEmail;
        }
    }
}
