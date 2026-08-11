package com.converter.docxjats.service.jats;

import java.util.ArrayList;
import java.util.List;

/**
 * Construye el bloque {@code <front>} del artículo JATS: metadatos de la
 * revista, título/subtítulo del artículo, autores (contrib-group), afiliaciones,
 * notas de autor, resumen, palabras clave, financiamiento, etc.
 */
public class JatsFrontBuilder {

    private record Author(String name, String affiliation, String orcid) {}
    
    private record Affiliation(String id, String label, String original, String normalized, 
                               String orgdiv1, String orgdiv2, String orgname, 
                               String state, String country, String email) {}

    private String spsVersion = "sps-1.9";
    private String lang = "es";
    private String title;
    private String subtitle;
    private String transTitle;
    private final List<Author> authors = new ArrayList<>();
    private final List<Affiliation> affiliations = new ArrayList<>();
    private final StringBuilder abstractBody = new StringBuilder();
    private final StringBuilder transAbstractBody = new StringBuilder();
    private final List<String> keywordsEs = new ArrayList<>();
    private final List<String> keywordsEn = new ArrayList<>();
    
    private String journalName = "Salud Colectiva";
    private String journalAbbrev = "Salud Colect";
    private String journalId = "scol";
    private String publisherName = "Universidad Nacional de Lanús";
    private String issnPrint = "1669-2381";
    private String issnElectronic = "1851-8265";
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

    public void setSpsVersion(String spsVersion) {
        if (spsVersion != null && !spsVersion.isBlank()) this.spsVersion = spsVersion.trim();
    }

    public void setLang(String lang) {
        if (lang != null && !lang.isBlank()) this.lang = lang.trim();
    }

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

    public void appendAuthor(String name, String affiliation) {
        appendAuthor(name, affiliation, null);
    }
    
    public void appendAuthor(String name, String affiliation, String orcid) {
        if (name == null || name.isBlank()) return;
        authors.add(new Author(name.trim(), 
            affiliation == null || affiliation.isBlank() ? null : affiliation.trim(),
            orcid));
    }
    
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

    public void appendAbstractParagraph(String runsXml) {
        if (runsXml == null || runsXml.isBlank()) return;
        abstractBody.append("<p>").append(runsXml).append("</p>\n");
    }
    
    public void appendTransAbstractParagraph(String runsXml) {
        if (runsXml == null || runsXml.isBlank()) return;
        transAbstractBody.append("<p>").append(runsXml).append("</p>\n");
    }

    public boolean hasAbstract() {
        return abstractBody.length() > 0;
    }
    
    public boolean hasTransAbstract() {
        return transAbstractBody.length() > 0;
    }
    
    public void addKeywordEs(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            keywordsEs.add(keyword.trim());
        }
    }
    
    public void addKeywordEn(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            keywordsEn.add(keyword.trim());
        }
    }
    
    public void setJournalName(String journalName) {
        if (journalName != null && !journalName.isBlank()) this.journalName = journalName.trim();
    }
    
    public void setJournalAbbrev(String journalAbbrev) {
        if (journalAbbrev != null && !journalAbbrev.isBlank()) this.journalAbbrev = journalAbbrev.trim();
    }

    public void setJournalId(String journalId) {
        if (journalId != null && !journalId.isBlank()) this.journalId = journalId.trim();
    }
    
    public void setPublisherName(String publisherName) {
        if (publisherName != null && !publisherName.isBlank()) this.publisherName = publisherName.trim();
    }
    
    public void setIssnPrint(String issnPrint) {
        if (issnPrint != null && !issnPrint.isBlank()) this.issnPrint = issnPrint.trim();
    }
    
    public void setIssnElectronic(String issnElectronic) {
        if (issnElectronic != null && !issnElectronic.isBlank()) this.issnElectronic = issnElectronic.trim();
    }
    
    public void setArticleIdPublisher(String articleIdPublisher) {
        if (articleIdPublisher != null && !articleIdPublisher.isBlank()) this.articleIdPublisher = articleIdPublisher.trim();
    }
    
    public void setArticleIdDoi(String articleIdDoi) {
        if (articleIdDoi != null && !articleIdDoi.isBlank()) this.articleIdDoi = articleIdDoi.trim();
    }
    
    public void setVolume(String volume) {
        if (volume != null && !volume.isBlank()) this.volume = volume.trim();
    }
    
    public void setElocationId(String elocationId) {
        if (elocationId != null && !elocationId.isBlank()) this.elocationId = elocationId.trim();
    }

    public String build(String fallbackTitle) {
        String finalTitle = hasTitle() ? title : fallbackTitle;

        StringBuilder sb = new StringBuilder();
        
        sb.append("<front>\n");
        
        // ===== JOURNAL-META =====
        sb.append("<journal-meta>\n");
        sb.append("<journal-id journal-id-type=\"nlm-ta\">").append(XmlUtils.escape(journalAbbrev)).append("</journal-id>\n");
        sb.append("<journal-id journal-id-type=\"publisher-id\">").append(XmlUtils.escape(journalId)).append("</journal-id>\n");
        sb.append("<journal-title-group>\n");
        sb.append("<journal-title>").append(XmlUtils.escape(journalName)).append("</journal-title>\n");
        sb.append("<abbrev-journal-title abbrev-type=\"publisher\">").append(XmlUtils.escape(journalAbbrev)).append("</abbrev-journal-title>\n");
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
        
        if (!articleIdDoi.isBlank()) {
            sb.append("<article-id pub-id-type=\"doi\">").append(articleIdDoi).append("</article-id>\n");
        }
        sb.append("<article-id pub-id-type=\"publisher-id\">").append(articleIdPublisher).append("</article-id>\n");
        
        sb.append("<article-categories>\n");
        sb.append("<subj-group subj-group-type=\"heading\">\n");
        sb.append("<subject>Artículo</subject>\n");
        sb.append("</subj-group>\n");
        sb.append("</article-categories>\n");
        
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

        if (hasAuthors()) {
            sb.append("<contrib-group>\n");
            int authorIndex = 0;
            for (Author author : authors) {
                sb.append("<contrib contrib-type=\"author\">\n");
                if (author.orcid() != null && !author.orcid().isBlank()) {
                    sb.append("<contrib-id contrib-id-type=\"orcid\">").append(author.orcid()).append("</contrib-id>\n");
                }
                String[] nameParts = author.name().trim().split("\\s+");
                if (nameParts.length >= 2) {
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
                
                if (!affiliations.isEmpty() && authorIndex < affiliations.size()) {
                    sb.append("<xref ref-type=\"aff\" rid=\"aff").append(authorIndex + 1).append("\">\n");
                    sb.append("<sup>").append(authorIndex + 1).append("</sup>\n");
                    sb.append("</xref>\n");
                }
                sb.append("</contrib>\n");
                authorIndex++;
            }
            sb.append("</contrib-group>\n");
        }
        
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
                    String countryCode = aff.country().length() == 2 ? aff.country().toUpperCase() : "AR";
                    sb.append("<country country=\"").append(countryCode).append("\">").append(aff.country()).append("</country>\n");
                }
                sb.append("</addr-line>\n");
            }
            if (!aff.email().isBlank()) {
                sb.append("<email>").append(XmlUtils.escape(aff.email())).append("</email>\n");
            }
            sb.append("</aff>\n");
        }
        
        if (hasAbstract()) {
            sb.append("<abstract>\n");
            sb.append("<title>Resumen</title>\n");
            sb.append(abstractBody.toString());
            sb.append("</abstract>\n");
        }
        
        if (hasTransAbstract()) {
            sb.append("<trans-abstract xml:lang=\"en\">\n");
            sb.append("<title>Abstract</title>\n");
            sb.append(transAbstractBody.toString());
            sb.append("</trans-abstract>\n");
        }
        
        if (!keywordsEs.isEmpty()) {
            sb.append("<kwd-group xml:lang=\"es\">\n");
            sb.append("<title>Palabras claves:</title>\n");
            for (String kw : keywordsEs) {
                sb.append("<kwd>").append(XmlUtils.escape(kw)).append("</kwd>\n");
            }
            sb.append("</kwd-group>\n");
        }
        
        if (!keywordsEn.isEmpty()) {
            sb.append("<kwd-group xml:lang=\"en\">\n");
            sb.append("<title>Keywords:</title>\n");
            for (String kw : keywordsEn) {
                sb.append("<kwd>").append(XmlUtils.escape(kw)).append("</kwd>\n");
            }
            sb.append("</kwd-group>\n");
        }
        
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