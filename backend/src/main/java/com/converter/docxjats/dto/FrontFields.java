package com.converter.docxjats.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa los campos editables del &lt;front&gt; y del elemento raíz &lt;article&gt;
 * de acuerdo con los 4 bloques del esquema de publicación editorial multirevista
 * (SciELO SPS y JATS):
 *
 * Bloque 1: Tipo de artículo (article-type: research-article, review-article, etc.)
 * Bloque 2: Estándares raíz (dtd-version, specific-use/sps-version, xml:lang)
 * Bloque 3: Información de revista, editorial, DOI y sección temática
 * Bloque 4: Fechas de publicación, colección, volumen, elocation-id, historial y licencias
 * Más contenido: Títulos, resúmenes, palabras clave y autores.
 */
public class FrontFields {

    // Bloque 1: Tipo de artículo
    private String articleType = "research-article";

    // Bloque 2: Estándares raíz
    private String dtdVersion = "1.1";
    private String spsVersion = "sps-1.9";
    private String xmlLang = "es";

    // Bloque 3: Datos de Revista y Publicador
    private String journalTitle = "";
    private String journalAbbrev = "";
    private String journalIdNlmTa = "";
    private String journalIdPublisher = "";
    private String issnPpub = "";
    private String issnEpub = "";
    private String publisherName = "";
    private String doi = "";
    private String subject = "";

    // Bloque 4: Fechas, numeración, historial y licencias
    private String pubDay = "";
    private String pubMonth = "";
    private String pubYear = "";
    private String collectionYear = "";
    private String volume = "";
    private String issue = "";
    private String elocationId = "";
    private String publisherId = "";

    private String historyReceivedDay = "";
    private String historyReceivedMonth = "";
    private String historyReceivedYear = "";

    private String historyAcceptedDay = "";
    private String historyAcceptedMonth = "";
    private String historyAcceptedYear = "";

    private String historyPubDay = "";
    private String historyPubMonth = "";
    private String historyPubYear = "";

    private String licenseType = "open-access";
    private String licenseUrl = "";
    private String licenseText = "";
    private String copyrightStatement = "";

    // Títulos y resúmenes
    private String articleTitle = "";
    private String subtitle = "";
    private String transTitle = "";
    private String abstractText = "";
    private String transAbstractText = "";
    private List<String> keywordsEs = new ArrayList<>();
    private List<String> keywordsEn = new ArrayList<>();
    private List<AuthorField> authors = new ArrayList<>();

    // Getters y Setters
    public String getArticleType() {
        return articleType;
    }

    public void setArticleType(String articleType) {
        this.articleType = articleType != null ? articleType : "research-article";
    }

    public String getDtdVersion() {
        return dtdVersion;
    }

    public void setDtdVersion(String dtdVersion) {
        this.dtdVersion = dtdVersion != null ? dtdVersion : "1.1";
    }

    public String getSpsVersion() {
        return spsVersion;
    }

    public void setSpsVersion(String spsVersion) {
        this.spsVersion = spsVersion != null ? spsVersion : "sps-1.9";
    }

    public String getXmlLang() {
        return xmlLang;
    }

    public void setXmlLang(String xmlLang) {
        this.xmlLang = xmlLang != null ? xmlLang : "es";
    }

    public String getJournalTitle() {
        return journalTitle;
    }

    public void setJournalTitle(String journalTitle) {
        this.journalTitle = journalTitle != null ? journalTitle : "";
    }

    public String getJournalAbbrev() {
        return journalAbbrev;
    }

    public void setJournalAbbrev(String journalAbbrev) {
        this.journalAbbrev = journalAbbrev != null ? journalAbbrev : "";
    }

    public String getJournalIdNlmTa() {
        return journalIdNlmTa;
    }

    public void setJournalIdNlmTa(String journalIdNlmTa) {
        this.journalIdNlmTa = journalIdNlmTa != null ? journalIdNlmTa : "";
    }

    public String getJournalIdPublisher() {
        return journalIdPublisher;
    }

    public void setJournalIdPublisher(String journalIdPublisher) {
        this.journalIdPublisher = journalIdPublisher != null ? journalIdPublisher : "";
    }

    public String getIssnPpub() {
        return issnPpub;
    }

    public void setIssnPpub(String issnPpub) {
        this.issnPpub = issnPpub != null ? issnPpub : "";
    }

    public String getIssnEpub() {
        return issnEpub;
    }

    public void setIssnEpub(String issnEpub) {
        this.issnEpub = issnEpub != null ? issnEpub : "";
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName != null ? publisherName : "";
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi != null ? doi : "";
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject != null ? subject : "";
    }

    public String getPubDay() {
        return pubDay;
    }

    public void setPubDay(String pubDay) {
        this.pubDay = pubDay != null ? pubDay : "";
    }

    public String getPubMonth() {
        return pubMonth;
    }

    public void setPubMonth(String pubMonth) {
        this.pubMonth = pubMonth != null ? pubMonth : "";
    }

    public String getPubYear() {
        return pubYear;
    }

    public void setPubYear(String pubYear) {
        this.pubYear = pubYear != null ? pubYear : "";
    }

    public String getCollectionYear() {
        return collectionYear;
    }

    public void setCollectionYear(String collectionYear) {
        this.collectionYear = collectionYear != null ? collectionYear : "";
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume != null ? volume : "";
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue != null ? issue : "";
    }

    public String getElocationId() {
        return elocationId;
    }

    public void setElocationId(String elocationId) {
        this.elocationId = elocationId != null ? elocationId : "";
    }

    public String getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(String publisherId) {
        this.publisherId = publisherId != null ? publisherId : "";
    }

    public String getHistoryReceivedDay() {
        return historyReceivedDay;
    }

    public void setHistoryReceivedDay(String historyReceivedDay) {
        this.historyReceivedDay = historyReceivedDay != null ? historyReceivedDay : "";
    }

    public String getHistoryReceivedMonth() {
        return historyReceivedMonth;
    }

    public void setHistoryReceivedMonth(String historyReceivedMonth) {
        this.historyReceivedMonth = historyReceivedMonth != null ? historyReceivedMonth : "";
    }

    public String getHistoryReceivedYear() {
        return historyReceivedYear;
    }

    public void setHistoryReceivedYear(String historyReceivedYear) {
        this.historyReceivedYear = historyReceivedYear != null ? historyReceivedYear : "";
    }

    public String getHistoryAcceptedDay() {
        return historyAcceptedDay;
    }

    public void setHistoryAcceptedDay(String historyAcceptedDay) {
        this.historyAcceptedDay = historyAcceptedDay != null ? historyAcceptedDay : "";
    }

    public String getHistoryAcceptedMonth() {
        return historyAcceptedMonth;
    }

    public void setHistoryAcceptedMonth(String historyAcceptedMonth) {
        this.historyAcceptedMonth = historyAcceptedMonth != null ? historyAcceptedMonth : "";
    }

    public String getHistoryAcceptedYear() {
        return historyAcceptedYear;
    }

    public void setHistoryAcceptedYear(String historyAcceptedYear) {
        this.historyAcceptedYear = historyAcceptedYear != null ? historyAcceptedYear : "";
    }

    public String getHistoryPubDay() {
        return historyPubDay;
    }

    public void setHistoryPubDay(String historyPubDay) {
        this.historyPubDay = historyPubDay != null ? historyPubDay : "";
    }

    public String getHistoryPubMonth() {
        return historyPubMonth;
    }

    public void setHistoryPubMonth(String historyPubMonth) {
        this.historyPubMonth = historyPubMonth != null ? historyPubMonth : "";
    }

    public String getHistoryPubYear() {
        return historyPubYear;
    }

    public void setHistoryPubYear(String historyPubYear) {
        this.historyPubYear = historyPubYear != null ? historyPubYear : "";
    }

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType != null ? licenseType : "open-access";
    }

    public String getLicenseUrl() {
        return licenseUrl;
    }

    public void setLicenseUrl(String licenseUrl) {
        this.licenseUrl = licenseUrl != null ? licenseUrl : "";
    }

    public String getLicenseText() {
        return licenseText;
    }

    public void setLicenseText(String licenseText) {
        this.licenseText = licenseText != null ? licenseText : "";
    }

    public String getCopyrightStatement() {
        return copyrightStatement;
    }

    public void setCopyrightStatement(String copyrightStatement) {
        this.copyrightStatement = copyrightStatement != null ? copyrightStatement : "";
    }

    public String getArticleTitle() {
        return articleTitle;
    }

    public void setArticleTitle(String articleTitle) {
        this.articleTitle = articleTitle != null ? articleTitle : "";
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle != null ? subtitle : "";
    }

    public String getTransTitle() {
        return transTitle;
    }

    public void setTransTitle(String transTitle) {
        this.transTitle = transTitle != null ? transTitle : "";
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText != null ? abstractText : "";
    }

    public String getTransAbstractText() {
        return transAbstractText;
    }

    public void setTransAbstractText(String transAbstractText) {
        this.transAbstractText = transAbstractText != null ? transAbstractText : "";
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
            this.givenNames = givenNames != null ? givenNames : "";
        }

        public String getSurname() {
            return surname;
        }

        public void setSurname(String surname) {
            this.surname = surname != null ? surname : "";
        }

        public String getOrcid() {
            return orcid;
        }

        public void setOrcid(String orcid) {
            this.orcid = orcid != null ? orcid : "";
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
            this.correspEmail = correspEmail != null ? correspEmail : "";
        }
    }
}
