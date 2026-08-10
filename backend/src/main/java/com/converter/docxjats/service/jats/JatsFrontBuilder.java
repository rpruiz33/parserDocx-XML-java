package com.converter.docxjats.service.jats;

import java.util.ArrayList;
import java.util.List;

/**
 * Construye el bloque {@code <front>} del artículo JATS: metadatos de la
 * revista, título/subtítulo del artículo, autores (contrib-group), afiliaciones,
 * notas de autor, resumen, palabras clave, financiamiento, etc.
 */
public class JatsFrontBuilder {

    /** Un autor detectado en la sección "Autores" del .docx (nombre + afiliación opcional). */
    private record Author(String name, String affiliation, String orcid) {}
    
    /** Una afiliación con sus componentes estructurados. */
    private record Affiliation(String id, String label, String original, String normalized, 
                               String orgdiv1, String orgdiv2, String orgname, 
                               String state, String country, String email) {}

    private String title;
    private String subtitle;
    private String transTitle; // Título traducido (inglés por defecto)
    private final List<Author> authors = new ArrayList<>();
    private final List<Affiliation> affiliations = new ArrayList<>();
    private final StringBuilder abstractBody = new StringBuilder();
    private final StringBuilder transAbstractBody = new StringBuilder();
    private final List<String> keywordsEs = new ArrayList<>();
    private final List<String> keywordsEn = new ArrayList<>();
    private String journalName = "TBD";
    private String journalId = "TBD";
    private String publisherName = "TBD";
    private String issnPrint = "";
    private String issnElectronic = "";
    private String articleIdPublisher = "TBD";
    private String articleIdDoi = "";
    private String volume = "";
    private String elocationId = "";
    private String pubDateDay = "";
    private String pubDateMonth = "";
    private String pubDateYear = "";
    private String collectionYear = "";
    private final List<String> historyReceived = new ArrayList<>();
    private final List<String> historyRevRecd = new ArrayList<>();
    private final List<String> historyAccepted = new ArrayList<>();
    private String licenseUrl = "";
    private String licenseText = "";
    private String fundingSource = "";
    private String awardId = "";
    private int figCount = 0;
    private int tableCount = 0;
    private int refCount = 0;

    public boolean hasTitle() {
        return title != null;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }
    
    public void setTransTitle(String transTitle) {
        this.transTitle = transTitle;
    }

    /**
     * Agrega un autor detectado en la sección "Autores" del .docx.
     * {@code affiliation} puede ser null si no se especificó afiliación.
     */
    public void appendAuthor(String name, String affiliation) {
        appendAuthor(name, affiliation, null);
    }
    
    /**
     * Agrega un autor con ORCID opcional.
     */
    public void appendAuthor(String name, String affiliation, String orcid) {
        if (name == null || name.isBlank()) return;
        authors.add(new Author(name.trim(), 
            affiliation == null || affiliation.isBlank() ? null : affiliation.trim(),
            orcid));
    }
    
    /**
     * Agrega una afiliación estructurada.
     */
    public void appendAffiliation(String id, String label, String original, String normalized,
                                  String orgdiv1, String orgdiv2, String orgname,
                                  String state, String country, String email) {
        if (id == null || id.isBlank()) return;
        affiliations.add(new Affiliation(id.trim(), 
            label != null ? label.trim() : "",
            original != null ? original.trim() : "",
            normalized != null ? normalized.trim() : "",
            orgdiv1 != null ? orgdiv1.trim() : "",
            orgdiv2 != null ? orgdiv2.trim() : "",
            orgname != null ? orgname.trim() : "",
            state != null ? state.trim() : "",
            country != null ? country.trim() : "",
            email != null ? email.trim() : ""));
    }

    public boolean hasAuthors() {
        return !authors.isEmpty();
    }

    /** Agrega un párrafo ya renderizado (con <bold>/<italic>/etc.) al resumen. */
    public void appendAbstractParagraph(String runsXml) {
        if (runsXml == null || runsXml.isBlank()) return;
        abstractBody.append("<p>\n").append(runsXml).append("\n</p>\n");
    }
    
    /** Agrega un párrafo al resumen traducido. */
    public void appendTransAbstractParagraph(String runsXml) {
        if (runsXml == null || runsXml.isBlank()) return;
        transAbstractBody.append("<p>\n").append(runsXml).append("\n</p>\n");
    }

    public boolean hasAbstract() {
        return abstractBody.length() > 0;
    }
    
    public boolean hasTransAbstract() {
        return transAbstractBody.length() > 0;
    }
    
    /** Agrega una palabra clave en español. */
    public void addKeywordEs(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            keywordsEs.add(keyword.trim());
        }
    }
    
    /** Agrega una palabra clave en inglés. */
    public void addKeywordEn(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            keywordsEn.add(keyword.trim());
        }
    }
    
    public void setJournalName(String journalName) {
        this.journalName = journalName != null ? journalName.trim() : "TBD";
    }
    
    public void setJournalId(String journalId) {
        this.journalId = journalId != null ? journalId.trim() : "TBD";
    }
    
    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName != null ? publisherName.trim() : "TBD";
    }
    
    public void setIssnPrint(String issnPrint) {
        this.issnPrint = issnPrint != null ? issnPrint.trim() : "";
    }
    
    public void setIssnElectronic(String issnElectronic) {
        this.issnElectronic = issnElectronic != null ? issnElectronic.trim() : "";
    }
    
    public void setArticleIdPublisher(String articleIdPublisher) {
        this.articleIdPublisher = articleIdPublisher != null ? articleIdPublisher.trim() : "TBD";
    }
    
    public void setArticleIdDoi(String articleIdDoi) {
        this.articleIdDoi = articleIdDoi != null ? articleIdDoi.trim() : "";
    }
    
    public void setVolume(String volume) {
        this.volume = volume != null ? volume.trim() : "";
    }
    
    public void setElocationId(String elocationId) {
        this.elocationId = elocationId != null ? elocationId.trim() : "";
    }
    
    public void setPubDate(String day, String month, String year) {
        this.pubDateDay = day != null ? day.trim() : "";
        this.pubDateMonth = month != null ? month.trim() : "";
        this.pubDateYear = year != null ? year.trim() : "";
    }
    
    public void setCollectionYear(String year) {
        this.collectionYear = year != null ? year.trim() : "";
    }
    
    public void addHistoryReceived(String day, String month, String year) {
        historyReceived.clear();
        if (day != null) historyReceived.add(day.trim());
        if (month != null) historyReceived.add(month.trim());
        if (year != null) historyReceived.add(year.trim());
    }
    
    public void addHistoryRevRecd(String day, String month, String year) {
        historyRevRecd.clear();
        if (day != null) historyRevRecd.add(day.trim());
        if (month != null) historyRevRecd.add(month.trim());
        if (year != null) historyRevRecd.add(year.trim());
    }
    
    public void addHistoryAccepted(String day, String month, String year) {
        historyAccepted.clear();
        if (day != null) historyAccepted.add(day.trim());
        if (month != null) historyAccepted.add(month.trim());
        if (year != null) historyAccepted.add(year.trim());
    }
    
    public void setLicense(String url, String text) {
        this.licenseUrl = url != null ? url.trim() : "";
        this.licenseText = text != null ? text.trim() : "";
    }
    
    public void setFunding(String source, String awardId) {
        this.fundingSource = source != null ? source.trim() : "";
        this.awardId = awardId != null ? awardId.trim() : "";
    }
    
    public void setFigCount(int count) {
        this.figCount = count;
    }
    
    public void setTableCount(int count) {
        this.tableCount = count;
    }
    
    public void setRefCount(int count) {
        this.refCount = count;
    }

    /**
     * Genera el XML del bloque front siguiendo el estándar JATS v1.3.
     * El orden de los elementos dentro de {@code <article-meta>} respeta el
     * modelo de contenido de JATS (Z39.96): title-group, contrib-group,
     * aff, author-notes, pub-date, history, permissions, abstract, kwd-group, funding-group, counts.
     */
    public String build(String fallbackTitle) {
        String finalTitle = hasTitle() ? title : fallbackTitle;

        StringBuilder sb = new StringBuilder();
        
        // ===== FRONT OPENING =====
        sb.append("<front>\n");
        
        // ===== JOURNAL-META =====
        sb.append("<journal-meta>\n");
        sb.append("<journal-id journal-id-type=\"nlm-ta\">").append(XmlUtils.escape(journalName)).append("</journal-id>\n");
        sb.append("<journal-id journal-id-type=\"publisher-id\">").append(XmlUtils.escape(journalId)).append("</journal-id>\n");
        sb.append("<journal-title-group>\n");
        sb.append("<journal-title>").append(XmlUtils.escape(journalName)).append("</journal-title>\n");
        sb.append("<abbrev-journal-title abbrev-type=\"publisher\">").append(XmlUtils.escape(journalName)).append("</abbrev-journal-title>\n");
        sb.append("</journal-title-group>\n");
        if (!issnPrint.isBlank()) {
            sb.append("<issn pub-type=\"ppub\">").append(issnPrint).append("</issn>\n");
        }
        if (!issnElectronic.isBlank()) {
            sb.append("<issn pub-type=\"epub\">").append(issnElectronic).append("</issn>\n");
        }
        sb.append("<publisher>\n");
        sb.append("<publisher-name>").append(XmlUtils.escape(publisherName)).append("</publisher-name>\n");
        sb.append("</publisher>\n");
        sb.append("</journal-meta>\n");

        // ===== ARTICLE-META =====
        sb.append("<article-meta>\n");
        
        // Article IDs
        if (!articleIdDoi.isBlank()) {
            sb.append("<article-id pub-id-type=\"doi\">").append(articleIdDoi).append("</article-id>\n");
        }
        sb.append("<article-id pub-id-type=\"publisher-id\">").append(articleIdPublisher).append("</article-id>\n");
        
        // Article categories
        sb.append("<article-categories>\n");
        sb.append("<subj-group subj-group-type=\"heading\">\n");
        sb.append("<subject>Artículo</subject>\n");
        sb.append("</subj-group>\n");
        sb.append("</article-categories>\n");
        
        // Title group
        sb.append("<title-group>\n");
        sb.append("<article-title>").append(XmlUtils.escape(finalTitle)).append("</article-title>\n");
        if (subtitle != null && !subtitle.isBlank()) {
            sb.append("<subtitle>").append(XmlUtils.escape(subtitle)).append("</subtitle>\n");
        }
        if (transTitle != null && !transTitle.isBlank()) {
            sb.append("<trans-title-group xml:lang=\"en\">\n");
            sb.append("<trans-title>").append(XmlUtils.escape(transTitle)).append("</trans-title>\n");
            sb.append("</trans-title-group>\n");
        }
        sb.append("</title-group>\n");

        // Contrib group (autores)
        if (hasAuthors()) {
            sb.append("<contrib-group>\n");
            int authorIndex = 0;
            for (Author author : authors) {
                sb.append("<contrib contrib-type=\"author\">\n");
                if (author.orcid() != null && !author.orcid().isBlank()) {
                    sb.append("<contrib-id contrib-id-type=\"orcid\">").append(author.orcid()).append("</contrib-id>\n");
                }
                // Parsear nombre: asumimos formato "Apellido Nombre" o "Nombre Apellido"
                String[] nameParts = author.name().trim().split("\\s+");
                if (nameParts.length >= 2) {
                    // Asumimos último elemento como apellido
                    sb.append("<name>\n");
                    sb.append("<surname>").append(XmlUtils.escape(nameParts[nameParts.length - 1])).append("</surname>\n");
                    sb.append("<given-names>");
                    for (int i = 0; i < nameParts.length - 1; i++) {
                        if (i > 0) sb.append(" ");
                        sb.append(XmlUtils.escape(nameParts[i]));
                    }
                    sb.append("</given-names>\n");
                    sb.append("</name>\n");
                } else {
                    sb.append("<string-name>").append(XmlUtils.escape(author.name())).append("</string-name>\n");
                }
                
                // Referencia a afiliación
                if (!affiliations.isEmpty() && authorIndex < affiliations.size()) {
                    Affiliation aff = affiliations.get(authorIndex);
                    sb.append("<xref ref-type=\"aff\" rid=\"aff").append(authorIndex + 1).append("\">\n");
                    sb.append("<sup>").append(authorIndex + 1).append("</sup>\n");
                    sb.append("</xref>\n");
                }
                sb.append("</contrib>\n");
                authorIndex++;
            }
            sb.append("</contrib-group>\n");
        }
        
        // Affiliations
        for (int i = 0; i < affiliations.size(); i++) {
            Affiliation aff = affiliations.get(i);
            sb.append("<aff id=\"aff").append(i + 1).append("\">\n");
            if (!aff.label().isBlank()) {
                sb.append("<label>").append(XmlUtils.escape(aff.label())).append("</label>\n");
            }
            if (!aff.original().isBlank()) {
                sb.append("<institution content-type=\"original\">").append(XmlUtils.escape(aff.original())).append("</institution>\n");
            }
            if (!aff.normalized().isBlank()) {
                sb.append("<institution content-type=\"normalized\">").append(XmlUtils.escape(aff.normalized())).append("</institution>\n");
            }
            if (!aff.orgdiv1().isBlank()) {
                sb.append("<institution content-type=\"orgdiv1\">").append(XmlUtils.escape(aff.orgdiv1())).append("</institution>\n");
            }
            if (!aff.orgdiv2().isBlank()) {
                sb.append("<institution content-type=\"orgdiv2\">").append(XmlUtils.escape(aff.orgdiv2())).append("</institution>\n");
            }
            if (!aff.orgname().isBlank()) {
                sb.append("<institution content-type=\"orgname\">").append(XmlUtils.escape(aff.orgname())).append("</institution>\n");
            }
            if (!aff.state().isBlank() || !aff.country().isBlank()) {
                sb.append("<addr-line>\n");
                if (!aff.state().isBlank()) {
                    sb.append("<state>").append(XmlUtils.escape(aff.state())).append("</state>\n");
                }
                if (!aff.country().isBlank()) {
                    String countryCode = aff.country().length() == 2 ? aff.country().toUpperCase() : "BR";
                    sb.append("<country country=\"").append(countryCode).append("\">").append(aff.country()).append("</country>\n");
                }
                sb.append("</addr-line>\n");
            }
            if (!aff.email().isBlank()) {
                sb.append("<email>").append(XmlUtils.escape(aff.email())).append("</email>\n");
            }
            sb.append("</aff>\n");
        }
        
        // Author notes (conflicto de intereses, contribución autoral)
        sb.append("<author-notes>\n");
        sb.append("<fn fn-type=\"conflict\" id=\"fn2\">\n");
        sb.append("<label>Conflicto de Intereses</label>\n");
        sb.append("<p>Los autores declaran no tener vínculos que condicionen lo expresado en el texto y que puedan ser comprendidos como conflicto de intereses.</p>\n");
        sb.append("</fn>\n");
        sb.append("<fn fn-type=\"equal\" id=\"fn3\">\n");
        sb.append("<label>Contribución autoral</label>\n");
        sb.append("<p>Todos los autores revisaron y aprobaron la versión final del manuscrito.</p>\n");
        sb.append("</fn>\n");
        sb.append("</author-notes>\n");
        
        // Publication dates
        if (!pubDateYear.isBlank()) {
            sb.append("<pub-date date-type=\"pub\" publication-format=\"electronic\">\n");
            if (!pubDateDay.isBlank()) sb.append("<day>").append(pubDateDay).append("</day>\n");
            if (!pubDateMonth.isBlank()) sb.append("<month>").append(pubDateMonth).append("</month>\n");
            sb.append("<year>").append(pubDateYear).append("</year>\n");
            sb.append("</pub-date>\n");
        }
        if (!collectionYear.isBlank()) {
            sb.append("<pub-date date-type=\"collection\" publication-format=\"electronic\">\n");
            sb.append("<year>").append(collectionYear).append("</year>\n");
            sb.append("</pub-date>\n");
        }
        
        // Volume and elocation-id
        if (!volume.isBlank()) {
            sb.append("<volume>").append(volume).append("</volume>\n");
        }
        if (!elocationId.isBlank()) {
            sb.append("<elocation-id>").append(elocationId).append("</elocation-id>\n");
        }
        
        // History
        if (!historyReceived.isEmpty() || !historyRevRecd.isEmpty() || !historyAccepted.isEmpty()) {
            sb.append("<history>\n");
            if (!historyReceived.isEmpty() && historyReceived.size() >= 3) {
                sb.append("<date date-type=\"received\">\n");
                sb.append("<day>").append(historyReceived.get(0)).append("</day>\n");
                sb.append("<month>").append(historyReceived.get(1)).append("</month>\n");
                sb.append("<year>").append(historyReceived.get(2)).append("</year>\n");
                sb.append("</date>\n");
            }
            if (!historyRevRecd.isEmpty() && historyRevRecd.size() >= 3) {
                sb.append("<date date-type=\"rev-recd\">\n");
                sb.append("<day>").append(historyRevRecd.get(0)).append("</day>\n");
                sb.append("<month>").append(historyRevRecd.get(1)).append("</month>\n");
                sb.append("<year>").append(historyRevRecd.get(2)).append("</year>\n");
                sb.append("</date>\n");
            }
            if (!historyAccepted.isEmpty() && historyAccepted.size() >= 3) {
                sb.append("<date date-type=\"accepted\">\n");
                sb.append("<day>").append(historyAccepted.get(0)).append("</day>\n");
                sb.append("<month>").append(historyAccepted.get(1)).append("</month>\n");
                sb.append("<year>").append(historyAccepted.get(2)).append("</year>\n");
                sb.append("</date>\n");
            }
            sb.append("</history>\n");
        }
        
        // Permissions / License
        if (!licenseUrl.isBlank() || !licenseText.isBlank()) {
            sb.append("<permissions>\n");
            sb.append("<license license-type=\"open-access\" xlink:href=\"").append(licenseUrl).append("\" xml:lang=\"es\">\n");
            sb.append("<license-p>").append(XmlUtils.escape(licenseText)).append("</license-p>\n");
            sb.append("</license>\n");
            sb.append("</permissions>\n");
        }
        
        // Abstract
        if (hasAbstract()) {
            sb.append("<abstract>\n");
            sb.append("<title>Resumen</title>\n");
            sb.append(abstractBody.toString());
            sb.append("</abstract>\n");
        }
        
        // Translated abstract
        if (hasTransAbstract()) {
            sb.append("<trans-abstract xml:lang=\"en\">\n");
            sb.append("<title>Abstract</title>\n");
            sb.append(transAbstractBody.toString());
            sb.append("</trans-abstract>\n");
        }
        
        // Keywords Spanish
        if (!keywordsEs.isEmpty()) {
            sb.append("<kwd-group xml:lang=\"es\">\n");
            sb.append("<title>Palabras claves:</title>\n");
            for (String kw : keywordsEs) {
                sb.append("<kwd>").append(XmlUtils.escape(kw)).append("</kwd>\n");
            }
            sb.append("</kwd-group>\n");
        }
        
        // Keywords English
        if (!keywordsEn.isEmpty()) {
            sb.append("<kwd-group xml:lang=\"en\">\n");
            sb.append("<title>Keywords:</title>\n");
            for (String kw : keywordsEn) {
                sb.append("<kwd>").append(XmlUtils.escape(kw)).append("</kwd>\n");
            }
            sb.append("</kwd-group>\n");
        }
        
        // Funding group
        if (!fundingSource.isBlank()) {
            sb.append("<funding-group>\n");
            sb.append("<award-group award-type=\"contract\">\n");
            sb.append("<funding-source>").append(XmlUtils.escape(fundingSource)).append("</funding-source>\n");
            if (!awardId.isBlank()) {
                sb.append("<award-id>").append(awardId).append("</award-id>\n");
            }
            sb.append("</award-group>\n");
            sb.append("</funding-group>\n");
        }
        
        // Counts
        sb.append("<counts>\n");
        sb.append("<fig-count count=\"").append(figCount).append("\"/>\n");
        sb.append("<table-count count=\"").append(tableCount).append("\"/>\n");
        sb.append("<equation-count count=\"0\"/>\n");
        sb.append("<ref-count count=\"").append(refCount).append("\"/>\n");
        sb.append("<page-count count=\"1\"/>\n");
        sb.append("</counts>\n");
        
        sb.append("</article-meta>\n");
        sb.append("</front>\n");
        
        return sb.toString();
    }
}
