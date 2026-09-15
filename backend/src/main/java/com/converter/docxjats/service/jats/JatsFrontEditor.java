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
 * Lee y reescribe, sobre un XML JATS ya generado, el subconjunto de campos
 * del &lt;front&gt; pensado para "edición rápida" desde el editor: título,
 * subtítulo, título traducido, resumen (es/en), palabras clave (es/en) y
 * datos básicos de autores (nombre, ORCID, email de correspondencia).
 *
 * No reestructura contrib-group/affiliations ni agrega o quita autores:
 * edita in-place los nodos que ya existen en el XML de entrada.
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
        Element articleMeta = firstByTag(doc, "article-meta");
        if (articleMeta == null) {
            throw new IllegalArgumentException("El XML no tiene <article-meta>; no se puede editar el front.");
        }

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
            setText(doc, title, defaultTitle);
            el.appendChild(title);
            insertInArticleMetaOrder(articleMeta, el,
                    "trans-abstract", "kwd-group", "funding-group", "counts");
        }

        for (Element p : allDirectChildrenByTag(el, "p")) {
            el.removeChild(p);
        }
        for (String paragraph : splitParagraphs(text)) {
            Element p = doc.createElement("p");
            setText(doc, p, paragraph);
            el.appendChild(p);
        }
    }

    private static void updateKeywords(Document doc, Element articleMeta, String lang,
                                        List<String> keywords, String defaultTitle) {
        Element group = null;
        for (Element g : allDirectChildrenByTag(articleMeta, "kwd-group")) {
            if (lang.equals(g.getAttribute("xml:lang"))) {
                group = g;
                break;
            }
        }

        List<String> clean = new ArrayList<>();
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw != null && !kw.isBlank()) clean.add(kw.trim());
            }
        }

        if (clean.isEmpty()) {
            if (group != null) articleMeta.removeChild(group);
            return;
        }

        if (group == null) {
            group = doc.createElement("kwd-group");
            group.setAttribute("xml:lang", lang);
            Element title = doc.createElement("title");
            setText(doc, title, defaultTitle);
            group.appendChild(title);
            insertInArticleMetaOrder(articleMeta, group, "funding-group", "counts");
        }

        for (Element kwd : allDirectChildrenByTag(group, "kwd")) {
            group.removeChild(kwd);
        }
        for (String kw : clean) {
            Element kwdEl = doc.createElement("kwd");
            setText(doc, kwdEl, kw);
            group.appendChild(kwdEl);
        }
    }

    private static void updateAuthors(Document doc, List<FrontFields.AuthorField> authors) {
        if (authors == null || authors.isEmpty()) return;

        NodeList contribs = doc.getElementsByTagName("contrib");
        List<Element> correspNodes = allDirectChildrenByTag(firstByTag(doc, "author-notes"), "corresp");
        int correspOrdinal = 0;

        int count = Math.min(authors.size(), contribs.getLength());
        for (int i = 0; i < count; i++) {
            Element contrib = (Element) contribs.item(i);
            FrontFields.AuthorField edit = authors.get(i);

            Element name = firstChildByTag(contrib, "name");
            if (name != null) {
                updateOrCreateChild(doc, name, "surname", edit.getSurname());
                updateOrCreateChild(doc, name, "given-names", edit.getGivenNames());
            } else {
                Element stringName = firstChildByTag(contrib, "string-name");
                if (stringName != null) {
                    String full = (nullToEmpty(edit.getGivenNames()) + " " + nullToEmpty(edit.getSurname())).trim();
                    if (!full.isEmpty()) setText(doc, stringName, full);
                }
            }

            Element contribId = firstChildByTag(contrib, "contrib-id");
            String orcid = edit.getOrcid();
            if (orcid != null && !orcid.isBlank()) {
                if (contribId == null) {
                    contribId = doc.createElement("contrib-id");
                    contribId.setAttribute("contrib-id-type", "orcid");
                    contrib.insertBefore(contribId, contrib.getFirstChild());
                }
                setText(doc, contribId, orcid.trim());
            } else if (contribId != null && "orcid".equals(contribId.getAttribute("contrib-id-type"))) {
                contrib.removeChild(contribId);
            }

            boolean corresponding = "yes".equals(contrib.getAttribute("corresp"));
            if (corresponding && correspOrdinal < correspNodes.size()) {
                Element correspEl = correspNodes.get(correspOrdinal);
                String email = edit.getCorrespEmail();
                Element emailEl = firstChildByTag(correspEl, "email");
                if (email != null && !email.isBlank() && emailEl != null) {
                    setText(doc, emailEl, email.trim());
                }
                correspOrdinal++;
            }
        }
    }

    private static void updateOrCreateChild(Document doc, Element parent, String tag, String value) {
        if (value == null) return;
        Element el = firstChildByTag(parent, tag);
        if (el == null) {
            el = doc.createElement(tag);
            parent.appendChild(el);
        }
        setText(doc, el, value);
    }

    // ---------------------------------------------------------------
    // Utilidades DOM
    // ---------------------------------------------------------------

    private static void insertInArticleMetaOrder(Element articleMeta, Element newChild, String... followingTags) {
        for (String tag : followingTags) {
            for (Element candidate : allDirectChildrenByTag(articleMeta, tag)) {
                articleMeta.insertBefore(newChild, candidate);
                return;
            }
        }
        articleMeta.appendChild(newChild);
    }

    private static List<String> splitParagraphs(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("\\r?\\n\\s*\\r?\\n")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        if (out.isEmpty() && !text.isBlank()) out.add(text.trim());
        return out;
    }

    private static void setText(Document doc, Element el, String value) {
        while (el.getFirstChild() != null) {
            el.removeChild(el.getFirstChild());
        }
        el.appendChild(doc.createTextNode(value == null ? "" : value));
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
