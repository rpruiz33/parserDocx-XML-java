package com.converter.docxjats.service.jats;

import com.converter.docxjats.dto.FrontFields;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee y reescribe, sobre un XML JATS ya generado, los metadatos estructurados
 * conforme a los 4 bloques del estándar de publicación multirevista (SciELO SPS y JATS):
 *
 * Bloque 1: Tipo de artículo (article-type)
 * Bloque 2: Estándares raíz (dtd-version, specific-use/sps-version, xml:lang)
 * Bloque 3: Metadatos de revista, editorial, identificadores y sección
 * Bloque 4: Fechas de publicación, colección, volumen, elocation-id, historial y licencias
 * Y contenidos del front: Títulos, resúmenes, palabras clave y autores.
 */
public final class JatsFrontEditor {

    private static final Pattern DOCTYPE_PATTERN =
            Pattern.compile("<!DOCTYPE\\s+article\\s+PUBLIC\\s+\"([^\"]+)\"\\s+\"([^\"]+)\"");

    private JatsFrontEditor() {
    }

    // ---------------------------------------------------------------
    // Extracción
    // ---------------------------------------------------------------

    public static FrontFields extract(String xml) throws Exception {
        Document doc = parse(xml);
        FrontFields fields = new FrontFields();

        // Bloque 1 y 2: Raíz <article>
        Element article = doc.getDocumentElement();
        if (article != null) {
            if (article.hasAttribute("article-type")) fields.setArticleType(article.getAttribute("article-type"));
            if (article.hasAttribute("dtd-version")) fields.setDtdVersion(article.getAttribute("dtd-version"));
            if (article.hasAttribute("specific-use")) fields.setSpsVersion(article.getAttribute("specific-use"));
            if (article.hasAttribute("xml:lang")) fields.setXmlLang(article.getAttribute("xml:lang"));
        }

        // Bloque 3: Datos de Revista (<journal-meta>)
        Element journalMeta = firstByTag(doc, "journal-meta");
        if (journalMeta != null) {
            fields.setJournalTitle(textOf(firstByTag(journalMeta, "journal-title")));
            fields.setJournalAbbrev(textOf(firstByTag(journalMeta, "abbrev-journal-title")));
            fields.setPublisherName(textOf(firstByTag(journalMeta, "publisher-name")));

            NodeList journalIds = journalMeta.getElementsByTagName("journal-id");
            for (int i = 0; i < journalIds.getLength(); i++) {
                Element jId = (Element) journalIds.item(i);
                String type = jId.getAttribute("journal-id-type");
                if ("nlm-ta".equalsIgnoreCase(type)) {
                    fields.setJournalIdNlmTa(textOf(jId));
                } else if ("publisher-id".equalsIgnoreCase(type)) {
                    fields.setJournalIdPublisher(textOf(jId));
                }
            }

            NodeList issns = journalMeta.getElementsByTagName("issn");
            for (int i = 0; i < issns.getLength(); i++) {
                Element issn = (Element) issns.item(i);
                String type = issn.getAttribute("pub-type");
                if ("ppub".equalsIgnoreCase(type)) {
                    fields.setIssnPpub(textOf(issn));
                } else if ("epub".equalsIgnoreCase(type)) {
                    fields.setIssnEpub(textOf(issn));
                }
            }
        }

        // Bloque 3 y 4: Identificadores, Fechas y Metadatos (<article-meta>)
        Element articleMeta = firstByTag(doc, "article-meta");
        if (articleMeta != null) {
            NodeList articleIds = articleMeta.getElementsByTagName("article-id");
            for (int i = 0; i < articleIds.getLength(); i++) {
                Element aId = (Element) articleIds.item(i);
                String type = aId.getAttribute("pub-id-type");
                if ("doi".equalsIgnoreCase(type)) {
                    fields.setDoi(textOf(aId));
                } else if ("publisher-id".equalsIgnoreCase(type)) {
                    fields.setPublisherId(textOf(aId));
                }
            }

            Element subj = firstByTag(articleMeta, "subject");
            if (subj != null) fields.setSubject(textOf(subj));

            fields.setVolume(textOf(firstByTag(articleMeta, "volume")));
            fields.setIssue(textOf(firstByTag(articleMeta, "issue")));
            fields.setElocationId(textOf(firstByTag(articleMeta, "elocation-id")));

            NodeList pubDates = articleMeta.getElementsByTagName("pub-date");
            for (int i = 0; i < pubDates.getLength(); i++) {
                Element pd = (Element) pubDates.item(i);
                String dateType = pd.getAttribute("date-type");
                if ("pub".equalsIgnoreCase(dateType)) {
                    fields.setPubDay(textOf(firstByTag(pd, "day")));
                    fields.setPubMonth(textOf(firstByTag(pd, "month")));
                    fields.setPubYear(textOf(firstByTag(pd, "year")));
                } else if ("collection".equalsIgnoreCase(dateType)) {
                    fields.setCollectionYear(textOf(firstByTag(pd, "year")));
                }
            }

            Element history = firstByTag(articleMeta, "history");
            if (history != null) {
                NodeList dates = history.getElementsByTagName("date");
                for (int i = 0; i < dates.getLength(); i++) {
                    Element d = (Element) dates.item(i);
                    String type = d.getAttribute("date-type");
                    if ("received".equalsIgnoreCase(type)) {
                        fields.setHistoryReceivedDay(textOf(firstByTag(d, "day")));
                        fields.setHistoryReceivedMonth(textOf(firstByTag(d, "month")));
                        fields.setHistoryReceivedYear(textOf(firstByTag(d, "year")));
                    } else if ("accepted".equalsIgnoreCase(type)) {
                        fields.setHistoryAcceptedDay(textOf(firstByTag(d, "day")));
                        fields.setHistoryAcceptedMonth(textOf(firstByTag(d, "month")));
                        fields.setHistoryAcceptedYear(textOf(firstByTag(d, "year")));
                    } else if ("pub".equalsIgnoreCase(type)) {
                        fields.setHistoryPubDay(textOf(firstByTag(d, "day")));
                        fields.setHistoryPubMonth(textOf(firstByTag(d, "month")));
                        fields.setHistoryPubYear(textOf(firstByTag(d, "year")));
                    }
                }
            }

            Element license = firstByTag(articleMeta, "license");
            if (license != null) {
                if (license.hasAttribute("license-type")) fields.setLicenseType(license.getAttribute("license-type"));
                if (license.hasAttribute("xlink:href")) fields.setLicenseUrl(license.getAttribute("xlink:href"));
                fields.setLicenseText(textOf(firstByTag(license, "license-p")));
            }
            fields.setCopyrightStatement(textOf(firstByTag(articleMeta, "copyright-statement")));
        }

        // Títulos, resúmenes, keywords y autores
        fields.setArticleTitle(textOf(firstByTag(doc, "article-title")));
        fields.setSubtitle(textOf(firstByTag(doc, "subtitle")));
        fields.setTransTitle(textOf(firstByTag(doc, "trans-title")));
        fields.setAbstractText(joinParagraphs(firstByTag(doc, "abstract")));
        fields.setTransAbstractText(joinParagraphs(firstByTag(doc, "trans-abstract")));
        fields.setKeywordsEs(keywordsFor(doc, "es"));
        fields.setKeywordsEn(keywordsFor(doc, "en"));
        fields.setAuthors(extractAuthors(doc));

        return fields;
    }

    private static List<String> keywordsFor(Document doc, String lang) {
        List<String> out = new ArrayList<>();
        NodeList groups = doc.getElementsByTagName("kwd-group");
        for (int i = 0; i < groups.getLength(); i++) {
            Element group = (Element) groups.item(i);
            if (lang.equals(group.getAttribute("xml:lang"))) {
                NodeList kwds = group.getElementsByTagName("kwd");
                for (int j = 0; j < kwds.getLength(); j++) {
                    String text = kwds.item(j).getTextContent();
                    if (text != null && !text.isBlank()) out.add(text.trim());
                }
                break;
            }
        }
        return out;
    }

    private static List<FrontFields.AuthorField> extractAuthors(Document doc) {
        List<FrontFields.AuthorField> authors = new ArrayList<>();
        NodeList contribs = doc.getElementsByTagName("contrib");
        int correspOrdinal = 0;
        List<Element> correspNodes = allDirectChildrenByTag(firstByTag(doc, "author-notes"), "corresp");

        for (int i = 0; i < contribs.getLength(); i++) {
            Element contrib = (Element) contribs.item(i);
            FrontFields.AuthorField author = new FrontFields.AuthorField();

            Element name = firstChildByTag(contrib, "name");
            if (name != null) {
                author.setSurname(textOf(firstChildByTag(name, "surname")));
                author.setGivenNames(textOf(firstChildByTag(name, "given-names")));
            } else {
                author.setSurname(textOf(firstChildByTag(contrib, "string-name")));
            }

            Element contribId = firstChildByTag(contrib, "contrib-id");
            if (contribId != null && "orcid".equals(contribId.getAttribute("contrib-id-type"))) {
                author.setOrcid(textOf(contribId));
            }

            boolean corresponding = "yes".equals(contrib.getAttribute("corresp"));
            author.setCorresponding(corresponding);
            if (corresponding && correspOrdinal < correspNodes.size()) {
                Element correspEl = correspNodes.get(correspOrdinal);
                author.setCorrespEmail(textOf(firstChildByTag(correspEl, "email")));
                correspOrdinal++;
            }

            authors.add(author);
        }
        return authors;
    }

    // ---------------------------------------------------------------
    // Aplicación de ediciones
    // ---------------------------------------------------------------

    public static String applyFront(String xml, FrontFields fields) throws Exception {
        Document doc = parse(xml);

        // Bloque 1 y 2: Atributos raíz <article>
        Element article = doc.getDocumentElement();
        if (article != null) {
            if (notBlank(fields.getArticleType())) article.setAttribute("article-type", fields.getArticleType().trim());
            if (notBlank(fields.getDtdVersion())) article.setAttribute("dtd-version", fields.getDtdVersion().trim());
            if (notBlank(fields.getSpsVersion())) article.setAttribute("specific-use", fields.getSpsVersion().trim());
            if (notBlank(fields.getXmlLang())) article.setAttribute("xml:lang", fields.getXmlLang().trim());
        }

        // Bloque 3: Datos de Revista (<journal-meta>)
        updateJournalMeta(doc, fields);

        // Bloque 3 y 4: Datos de Artículo (<article-meta>)
        Element articleMeta = firstByTag(doc, "article-meta");
        if (articleMeta == null) {
            throw new IllegalArgumentException("El XML no tiene <article-meta>; no se puede editar el front.");
        }

        updateArticleMeta(doc, articleMeta, fields);

        // Contenidos textuales
        updateArticleTitle(doc, fields.getArticleTitle());
        updateSubtitle(doc, fields.getSubtitle());
        updateTransTitle(doc, fields.getTransTitle());
        updateAbstractLike(doc, articleMeta, "abstract", fields.getAbstractText(), "Resumen", null);
        updateAbstractLike(doc, articleMeta, "trans-abstract", fields.getTransAbstractText(), "Abstract", "en");
        updateKeywords(doc, articleMeta, "es", fields.getKeywordsEs(), "Palabras claves:");
        updateKeywords(doc, articleMeta, "en", fields.getKeywordsEn(), "Keywords:");
        updateAuthors(doc, fields.getAuthors());

        return serialize(doc, xml);
    }

    private static void updateJournalMeta(Document doc, FrontFields fields) {
        Element journalMeta = firstByTag(doc, "journal-meta");
        if (journalMeta == null) return;

        if (notBlank(fields.getJournalTitle())) {
            Element titleGroup = firstChildByTag(journalMeta, "journal-title-group");
            if (titleGroup == null) {
                titleGroup = doc.createElement("journal-title-group");
                journalMeta.appendChild(titleGroup);
            }
            updateOrCreateChild(doc, titleGroup, "journal-title", fields.getJournalTitle());
            if (notBlank(fields.getJournalAbbrev())) {
                Element abbrev = updateOrCreateChild(doc, titleGroup, "abbrev-journal-title", fields.getJournalAbbrev());
                abbrev.setAttribute("abbrev-type", "publisher");
            }
        }

        if (notBlank(fields.getPublisherName())) {
            Element pub = firstChildByTag(journalMeta, "publisher");
            if (pub == null) {
                pub = doc.createElement("publisher");
                journalMeta.appendChild(pub);
            }
            updateOrCreateChild(doc, pub, "publisher-name", fields.getPublisherName());
        }

        if (notBlank(fields.getJournalIdNlmTa())) {
            updateOrCreateAttributeChild(doc, journalMeta, "journal-id", "journal-id-type", "nlm-ta", fields.getJournalIdNlmTa());
        }
        if (notBlank(fields.getJournalIdPublisher())) {
            updateOrCreateAttributeChild(doc, journalMeta, "journal-id", "journal-id-type", "publisher-id", fields.getJournalIdPublisher());
        }

        if (notBlank(fields.getIssnPpub())) {
            updateOrCreateAttributeChild(doc, journalMeta, "issn", "pub-type", "ppub", fields.getIssnPpub());
        }
        if (notBlank(fields.getIssnEpub())) {
            updateOrCreateAttributeChild(doc, journalMeta, "issn", "pub-type", "epub", fields.getIssnEpub());
        }
    }

    private static void updateArticleMeta(Document doc, Element articleMeta, FrontFields fields) {
        if (notBlank(fields.getDoi())) {
            updateOrCreateAttributeChild(doc, articleMeta, "article-id", "pub-id-type", "doi", fields.getDoi());
        }
        if (notBlank(fields.getPublisherId())) {
            updateOrCreateAttributeChild(doc, articleMeta, "article-id", "pub-id-type", "publisher-id", fields.getPublisherId());
        }

        if (notBlank(fields.getSubject())) {
            Element cat = firstChildByTag(articleMeta, "article-categories");
            if (cat == null) {
                cat = doc.createElement("article-categories");
                articleMeta.insertBefore(cat, articleMeta.getFirstChild());
            }
            Element subjGroup = firstChildByTag(cat, "subj-group");
            if (subjGroup == null) {
                subjGroup = doc.createElement("subj-group");
                subjGroup.setAttribute("subj-group-type", "heading");
                cat.appendChild(subjGroup);
            }
            updateOrCreateChild(doc, subjGroup, "subject", fields.getSubject());
        }

        if (notBlank(fields.getVolume())) updateOrCreateChild(doc, articleMeta, "volume", fields.getVolume());
        if (notBlank(fields.getIssue())) updateOrCreateChild(doc, articleMeta, "issue", fields.getIssue());
        if (notBlank(fields.getElocationId())) updateOrCreateChild(doc, articleMeta, "elocation-id", fields.getElocationId());

        // Pub-date
        if (notBlank(fields.getPubYear())) {
            Element pubDate = null;
            NodeList list = articleMeta.getElementsByTagName("pub-date");
            for (int i = 0; i < list.getLength(); i++) {
                Element pd = (Element) list.item(i);
                if ("pub".equalsIgnoreCase(pd.getAttribute("date-type"))) {
                    pubDate = pd;
                    break;
                }
            }
            if (pubDate == null) {
                pubDate = doc.createElement("pub-date");
                pubDate.setAttribute("date-type", "pub");
                pubDate.setAttribute("publication-format", "electronic");
                articleMeta.appendChild(pubDate);
            }
            if (notBlank(fields.getPubDay())) updateOrCreateChild(doc, pubDate, "day", fields.getPubDay());
            if (notBlank(fields.getPubMonth())) updateOrCreateChild(doc, pubDate, "month", fields.getPubMonth());
            updateOrCreateChild(doc, pubDate, "year", fields.getPubYear());
        }

        // Collection year
        if (notBlank(fields.getCollectionYear())) {
            Element colDate = null;
            NodeList list = articleMeta.getElementsByTagName("pub-date");
            for (int i = 0; i < list.getLength(); i++) {
                Element pd = (Element) list.item(i);
                if ("collection".equalsIgnoreCase(pd.getAttribute("date-type"))) {
                    colDate = pd;
                    break;
                }
            }
            if (colDate == null) {
                colDate = doc.createElement("pub-date");
                colDate.setAttribute("date-type", "collection");
                colDate.setAttribute("publication-format", "electronic");
                articleMeta.appendChild(colDate);
            }
            updateOrCreateChild(doc, colDate, "year", fields.getCollectionYear());
        }

        // Historial
        updateHistory(doc, articleMeta, fields);

        // Permisos y Licencias
        updatePermissions(doc, articleMeta, fields);
    }

    private static void updateHistory(Document doc, Element articleMeta, FrontFields fields) {
        boolean hasHistory = notBlank(fields.getHistoryReceivedYear())
                || notBlank(fields.getHistoryAcceptedYear())
                || notBlank(fields.getHistoryPubYear());
        if (!hasHistory) return;

        Element history = firstChildByTag(articleMeta, "history");
        if (history == null) {
            history = doc.createElement("history");
            articleMeta.appendChild(history);
        }

        updateHistoryDate(doc, history, "received", fields.getHistoryReceivedDay(), fields.getHistoryReceivedMonth(), fields.getHistoryReceivedYear());
        updateHistoryDate(doc, history, "accepted", fields.getHistoryAcceptedDay(), fields.getHistoryAcceptedMonth(), fields.getHistoryAcceptedYear());
        updateHistoryDate(doc, history, "pub", fields.getHistoryPubDay(), fields.getHistoryPubMonth(), fields.getHistoryPubYear());
    }

    private static void updateHistoryDate(Document doc, Element history, String dateType, String day, String month, String year) {
        if (!notBlank(year)) return;
        Element dateEl = null;
        NodeList list = history.getElementsByTagName("date");
        for (int i = 0; i < list.getLength(); i++) {
            Element d = (Element) list.item(i);
            if (dateType.equalsIgnoreCase(d.getAttribute("date-type"))) {
                dateEl = d;
                break;
            }
        }
        if (dateEl == null) {
            dateEl = doc.createElement("date");
            dateEl.setAttribute("date-type", dateType);
            history.appendChild(dateEl);
        }
        if (notBlank(day)) updateOrCreateChild(doc, dateEl, "day", day);
        if (notBlank(month)) updateOrCreateChild(doc, dateEl, "month", month);
        updateOrCreateChild(doc, dateEl, "year", year);
    }

    private static void updatePermissions(Document doc, Element articleMeta, FrontFields fields) {
        boolean hasPerms = notBlank(fields.getLicenseUrl()) || notBlank(fields.getLicenseText()) || notBlank(fields.getCopyrightStatement());
        if (!hasPerms) return;

        Element perms = firstChildByTag(articleMeta, "permissions");
        if (perms == null) {
            perms = doc.createElement("permissions");
            articleMeta.appendChild(perms);
        }

        if (notBlank(fields.getCopyrightStatement())) {
            updateOrCreateChild(doc, perms, "copyright-statement", fields.getCopyrightStatement());
        }

        if (notBlank(fields.getLicenseUrl()) || notBlank(fields.getLicenseText())) {
            Element docEl = doc.getDocumentElement();
            if (docEl != null && !docEl.hasAttribute("xmlns:xlink")) {
                docEl.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
            }
            Element license = firstChildByTag(perms, "license");
            if (license == null) {
                license = doc.createElement("license");
                perms.appendChild(license);
            }
            license.setAttribute("license-type", notBlank(fields.getLicenseType()) ? fields.getLicenseType() : "open-access");
            if (notBlank(fields.getLicenseUrl())) license.setAttribute("xlink:href", fields.getLicenseUrl());
            license.setAttribute("xml:lang", notBlank(fields.getXmlLang()) ? fields.getXmlLang() : "es");

            if (notBlank(fields.getLicenseText())) {
                updateOrCreateChild(doc, license, "license-p", fields.getLicenseText());
            }
        }
    }

    private static Element updateOrCreateChild(Document doc, Element parent, String tag, String value) {
        Element el = firstChildByTag(parent, tag);
        if (el == null) {
            el = doc.createElement(tag);
            parent.appendChild(el);
        }
        setText(doc, el, value);
        return el;
    }

    private static void updateOrCreateAttributeChild(Document doc, Element parent, String tag, String attrName, String attrValue, String textValue) {
        Element target = null;
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
            if (attrValue.equalsIgnoreCase(el.getAttribute(attrName))) {
                target = el;
                break;
            }
        }
        if (target == null) {
            target = doc.createElement(tag);
            target.setAttribute(attrName, attrValue);
            parent.appendChild(target);
        }
        setText(doc, target, textValue);
    }

    private static void updateArticleTitle(Document doc, String value) {
        Element el = firstByTag(doc, "article-title");
        if (el != null && value != null) {
            setText(doc, el, value);
        }
    }

    private static void updateSubtitle(Document doc, String value) {
        Element titleGroup = firstByTag(doc, "title-group");
        if (titleGroup == null) return;
        Element existing = firstChildByTag(titleGroup, "subtitle");
        if (value == null || value.isBlank()) {
            if (existing != null) titleGroup.removeChild(existing);
            return;
        }
        if (existing != null) {
            setText(doc, existing, value);
        } else {
            Element el = doc.createElement("subtitle");
            setText(doc, el, value);
            Element articleTitle = firstChildByTag(titleGroup, "article-title");
            if (articleTitle != null && articleTitle.getNextSibling() != null) {
                titleGroup.insertBefore(el, articleTitle.getNextSibling());
            } else {
                titleGroup.appendChild(el);
            }
        }
    }

    private static void updateTransTitle(Document doc, String value) {
        Element titleGroup = firstByTag(doc, "title-group");
        if (titleGroup == null) return;
        Element transGroup = firstChildByTag(titleGroup, "trans-title-group");

        if (value == null || value.isBlank()) {
            if (transGroup != null) titleGroup.removeChild(transGroup);
            return;
        }

        if (transGroup == null) {
            transGroup = doc.createElement("trans-title-group");
            transGroup.setAttribute("xml:lang", "en");
            titleGroup.appendChild(transGroup);
        }
        Element transTitle = firstChildByTag(transGroup, "trans-title");
        if (transTitle == null) {
            transTitle = doc.createElement("trans-title");
            transGroup.appendChild(transTitle);
        }
        setText(doc, transTitle, value);
    }

    private static void updateAbstractLike(Document doc, Element articleMeta, String tag, String text,
                                            String defaultTitle, String langAttr) {
        Element el = firstChildByTag(articleMeta, tag);

        if (text == null || text.isBlank()) {
            if (el != null) articleMeta.removeChild(el);
            return;
        }

        if (el == null) {
            el = doc.createElement(tag);
            if (langAttr != null) el.setAttribute("xml:lang", langAttr);
            Element title = doc.createElement("title");
            title.setTextContent(defaultTitle);
            el.appendChild(title);
            articleMeta.appendChild(el);
        }

        Element titleEl = firstChildByTag(el, "title");
        String titleText = titleEl != null ? titleEl.getTextContent() : defaultTitle;

        while (el.hasChildNodes()) {
            el.removeChild(el.getFirstChild());
        }

        Element newTitle = doc.createElement("title");
        newTitle.setTextContent(titleText);
        el.appendChild(newTitle);

        for (String paragraph : text.split("\\n\\n+")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;
            Element p = doc.createElement("p");
            p.setTextContent(trimmed);
            el.appendChild(p);
        }
    }

    private static void updateKeywords(Document doc, Element articleMeta, String lang,
                                       List<String> keywords, String defaultTitle) {
        Element targetGroup = null;
        NodeList groups = articleMeta.getElementsByTagName("kwd-group");
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            if (lang.equals(g.getAttribute("xml:lang"))) {
                targetGroup = g;
                break;
            }
        }

        if (keywords == null || keywords.isEmpty()) {
            if (targetGroup != null) articleMeta.removeChild(targetGroup);
            return;
        }

        if (targetGroup == null) {
            targetGroup = doc.createElement("kwd-group");
            targetGroup.setAttribute("xml:lang", lang);
            articleMeta.appendChild(targetGroup);
        }

        Element titleEl = firstChildByTag(targetGroup, "title");
        String titleText = titleEl != null ? titleEl.getTextContent() : defaultTitle;

        while (targetGroup.hasChildNodes()) {
            targetGroup.removeChild(targetGroup.getFirstChild());
        }

        Element newTitle = doc.createElement("title");
        newTitle.setTextContent(titleText);
        targetGroup.appendChild(newTitle);

        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) continue;
            Element kwdEl = doc.createElement("kwd");
            kwdEl.setTextContent(kw.trim());
            targetGroup.appendChild(kwdEl);
        }
    }

    private static void updateAuthors(Document doc, List<FrontFields.AuthorField> authors) {
        if (authors == null) return;
        NodeList contribs = doc.getElementsByTagName("contrib");
        int count = Math.min(authors.size(), contribs.getLength());
        List<Element> correspNodes = allDirectChildrenByTag(firstByTag(doc, "author-notes"), "corresp");
        int correspOrdinal = 0;

        for (int i = 0; i < count; i++) {
            Element contrib = (Element) contribs.item(i);
            FrontFields.AuthorField field = authors.get(i);

            Element name = firstChildByTag(contrib, "name");
            if (name != null) {
                updateChildText(doc, name, "surname", field.getSurname());
                updateChildText(doc, name, "given-names", field.getGivenNames());
            } else {
                Element stringName = firstChildByTag(contrib, "string-name");
                if (stringName != null) {
                    setText(doc, stringName, field.getGivenNames() + " " + field.getSurname());
                }
            }

            if (field.getOrcid() != null && !field.getOrcid().isBlank()) {
                Element contribId = firstChildByTag(contrib, "contrib-id");
                if (contribId == null) {
                    contribId = doc.createElement("contrib-id");
                    contribId.setAttribute("contrib-id-type", "orcid");
                    contrib.insertBefore(contribId, contrib.getFirstChild());
                }
                setText(doc, contribId, normalizeOrcid(field.getOrcid()));
            }

            if (field.isCorresponding() && correspOrdinal < correspNodes.size()) {
                Element correspEl = correspNodes.get(correspOrdinal);
                if (field.getCorrespEmail() != null && !field.getCorrespEmail().isBlank()) {
                    updateChildText(doc, correspEl, "email", field.getCorrespEmail());
                }
                correspOrdinal++;
            }
        }
    }

    private static void updateChildText(Document doc, Element parent, String tag, String value) {
        if (value == null) return;
        Element child = firstChildByTag(parent, tag);
        if (child == null) {
            child = doc.createElement(tag);
            parent.appendChild(child);
        }
        setText(doc, child, value);
    }

    private static String normalizeOrcid(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        if (s.matches("^\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$")) {
            return "https://orcid.org/" + s;
        }
        return s;
    }

    private static void setText(Document doc, Element el, String text) {
        while (el.hasChildNodes()) {
            el.removeChild(el.getFirstChild());
        }
        el.appendChild(doc.createTextNode(nullToEmpty(text)));
    }

    private static String joinParagraphs(Element el) {
        if (el == null) return "";
        List<String> parts = new ArrayList<>();
        for (Element p : allDirectChildrenByTag(el, "p")) {
            String text = p.getTextContent();
            if (text != null && !text.isBlank()) parts.add(text.trim());
        }
        return String.join("\n\n", parts);
    }

    private static String textOf(Element el) {
        if (el == null) return "";
        String text = el.getTextContent();
        return text == null ? "" : text.trim();
    }

    private static Element firstByTag(Node scope, String tag) {
        if (scope == null) return null;
        NodeList list = (scope instanceof Document)
                ? ((Document) scope).getElementsByTagName(tag)
                : ((Element) scope).getElementsByTagName(tag);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static Element firstChildByTag(Element parent, String tag) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private static List<Element> allDirectChildrenByTag(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        if (parent == null) return out;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
                out.add((Element) n);
            }
        }
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static String serialize(Document doc, String originalXml) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        Matcher m = DOCTYPE_PATTERN.matcher(originalXml);
        if (m.find()) {
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, m.group(1));
            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, m.group(2));
        }

        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
