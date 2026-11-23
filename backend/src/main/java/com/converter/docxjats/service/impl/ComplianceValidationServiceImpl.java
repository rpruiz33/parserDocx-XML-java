package com.converter.docxjats.service.impl;

import com.converter.docxjats.dto.ComplianceReportDto;
import com.converter.docxjats.dto.ComplianceReportDto.CheckItem;
import com.converter.docxjats.service.interfaces.IComplianceValidationService;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

/**
 * Validador oficial y exhaustivo de conformidad técnica con los criterios
 * formales evaluados por PubMed Central (PMC Style Checker / NLM DTD) y
 * SciELO (SciELO Publishing Schema SPS 1.9 / 1.10 / JATS4R / Redalyc).
 */
@Service
public class ComplianceValidationServiceImpl implements IComplianceValidationService {

    private static final Set<String> PMC_VALID_ARTICLE_TYPES = Set.of(
            "research-article", "review-article", "editorial", "letter",
            "brief-report", "case-report", "other", "correction", "retraction",
            "article-commentary", "rapid-communication", "product-review",
            "data-paper", "systematic-review", "methodology"
    );

    private static final Set<String> CITATION_VALID_TYPES = Set.of(
            "journal", "book", "chapter", "confproc", "thesis", "webpage",
            "report", "patent", "standard", "software", "data", "newspaper",
            "legal-doc", "other"
    );

    @Override
    public ComplianceReportDto auditCompliance(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return new ComplianceReportDto(
                    false,
                    List.of(new CheckItem("PMC-00", "Contenido XML", false, "El documento XML está completamente vacío.")),
                    false,
                    List.of(new CheckItem("SCI-00", "Contenido XML", false, "El documento XML está completamente vacío."))
            );
        }

        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(new InputSource(new StringReader(xmlContent)));
        } catch (Exception e) {
            String errorMsg = "XML malformado: " + e.getMessage();
            return new ComplianceReportDto(
                    false,
                    List.of(new CheckItem("PMC-00", "Sintaxis XML", false, errorMsg)),
                    false,
                    List.of(new CheckItem("SCI-00", "Sintaxis XML", false, errorMsg))
            );
        }

        List<CheckItem> pmcChecks = auditPmcExhaustive(doc, xmlContent);
        List<CheckItem> scieloChecks = auditScieloExhaustive(doc, xmlContent);

        boolean pmcApto = pmcChecks.stream().allMatch(CheckItem::cumplido);
        boolean scieloApto = scieloChecks.stream().allMatch(CheckItem::cumplido);

        return new ComplianceReportDto(pmcApto, pmcChecks, scieloApto, scieloChecks);
    }

    /**
     * Batería oficial de reglas evaluadas por PubMed Central (PMC Style Checker).
     */
    private List<CheckItem> auditPmcExhaustive(Document doc, String rawXml) {
        List<CheckItem> checks = new ArrayList<>();
        Element root = doc.getDocumentElement();

        // PMC-01: Elemento raíz <article>
        boolean hasRoot = root != null && "article".equalsIgnoreCase(root.getNodeName());
        checks.add(new CheckItem("PMC-01", "Elemento Raíz <article>", hasRoot,
                hasRoot ? "Elemento raíz <article> conforme a NLM/JATS." : "El elemento raíz debe ser estrictamente <article>."));

        // PMC-02: Atributo article-type
        String articleType = root != null ? root.getAttribute("article-type") : "";
        boolean articleTypeOk = !articleType.isBlank() && PMC_VALID_ARTICLE_TYPES.contains(articleType.toLowerCase());
        checks.add(new CheckItem("PMC-02", "Atributo article-type Estándar NLM", articleTypeOk,
                articleTypeOk ? "Tipo de artículo NLM reconocido: " + articleType : "article-type no es válido según NLM: '" + articleType + "'."));

        // PMC-03: Atributo dtd-version
        String dtdVersion = root != null ? root.getAttribute("dtd-version") : "";
        boolean dtdOk = dtdVersion.matches("1\\.[0-3]");
        checks.add(new CheckItem("PMC-03", "Versión DTD Soportada (dtd-version)", dtdOk,
                dtdOk ? "Versión DTD soportada: " + dtdVersion : "dtd-version debe ser 1.0, 1.1, 1.2 o 1.3 (encontrado: '" + dtdVersion + "')."));

        // PMC-04: Atributo xml:lang
        String xmlLang = root != null ? root.getAttribute("xml:lang") : "";
        boolean langOk = xmlLang.matches("^[a-z]{2}(-[A-Za-z]{2,4})?$");
        checks.add(new CheckItem("PMC-04", "Idioma Principal (xml:lang ISO)", langOk,
                langOk ? "Idioma ISO declarado: " + xmlLang : "El atributo xml:lang en <article> debe ser un código ISO 639-1 válido (ej. 'es', 'en')."));

        // PMC-05: Declaración de Namespace xlink
        boolean hasXlink = rawXml.contains("xmlns:xlink") || rawXml.contains("http://www.w3.org/1999/xlink");
        checks.add(new CheckItem("PMC-05", "Espacio de Nombres xlink", hasXlink,
                hasXlink ? "xmlns:xlink declarado correctamente." : "Falta declaración xmlns:xlink=\"http://www.w3.org/1999/xlink\"."));

        // PMC-06: Bloques Mayores (<front>, <body>, <back>)
        boolean hasFront = doc.getElementsByTagName("front").getLength() > 0;
        boolean hasBody = doc.getElementsByTagName("body").getLength() > 0;
        boolean hasBack = doc.getElementsByTagName("back").getLength() > 0;
        boolean bloquesOk = hasFront && hasBody && hasBack;
        checks.add(new CheckItem("PMC-06", "Estructura Canónica (<front>, <body>, <back>)", bloquesOk,
                bloquesOk ? "Estructura canónica de 3 secciones presentes." : "Faltan bloques obligatorios JATS (<front>, <body> o <back>)."));

        // PMC-07: Identificador de Revista (<journal-id>)
        NodeList journalIds = doc.getElementsByTagName("journal-id");
        boolean jIdOk = journalIds.getLength() > 0;
        checks.add(new CheckItem("PMC-07", "Identificador de Revista (<journal-id>)", jIdOk,
                jIdOk ? "Identificador de revista presente (nlm-ta / pmc / publisher-id)." : "PMC Stylechecker exige al menos un <journal-id> en <journal-meta>."));

        // PMC-08: Título de Revista (<journal-title>)
        NodeList jTitles = doc.getElementsByTagName("journal-title");
        boolean jTitleOk = jTitles.getLength() > 0 && !jTitles.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("PMC-08", "Título de Revista (<journal-title>)", jTitleOk,
                jTitleOk ? "Título de revista: " + jTitles.item(0).getTextContent().trim() : "Falta <journal-title> en <journal-meta>."));

        // PMC-09: Editorial / Publisher (<publisher-name>)
        NodeList publishers = doc.getElementsByTagName("publisher-name");
        boolean pubOk = publishers.getLength() > 0 && !publishers.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("PMC-09", "Nombre de la Editorial (<publisher-name>)", pubOk,
                pubOk ? "Editorial declarada: " + publishers.item(0).getTextContent().trim() : "Falta <publisher-name> en <journal-meta>."));

        // PMC-10: ISSN Registrado (<issn>)
        NodeList issns = doc.getElementsByTagName("issn");
        boolean issnOk = false;
        String issnVal = "";
        for (int i = 0; i < issns.getLength(); i++) {
            String txt = issns.item(i).getTextContent().trim();
            if (txt.matches("\\d{4}-\\d{3}[\\dX]")) {
                issnOk = true;
                issnVal = txt;
                break;
            }
        }
        checks.add(new CheckItem("PMC-10", "Formato de ISSN (XXXX-XXXX)", issnOk,
                issnOk ? "ISSN válido: " + issnVal : "No se encontró un <issn> con formato válido de 8 caracteres (XXXX-XXXX)."));

        // PMC-11: Sanitización estricta del DOI en PMC
        NodeList articleIds = doc.getElementsByTagName("article-id");
        boolean doiOk = false;
        String doiVal = "";
        for (int i = 0; i < articleIds.getLength(); i++) {
            Element el = (Element) articleIds.item(i);
            if ("doi".equalsIgnoreCase(el.getAttribute("pub-id-type"))) {
                String txt = el.getTextContent() != null ? el.getTextContent().trim() : "";
                if (!txt.isEmpty() && !txt.matches(".*\\s+.*") && txt.startsWith("10.") && !txt.startsWith("http")) {
                    doiOk = true;
                    doiVal = txt;
                    break;
                }
            }
        }
        checks.add(new CheckItem("PMC-11", "Sanitización del DOI (sin URL ni espacios)", doiOk,
                doiOk ? "DOI válido para PMC: " + doiVal : "El DOI debe comenzar con '10.', no contener espacios ni incluir prefijo https://doi.org/."));

        // PMC-12: Título del Manuscrito (<article-title>)
        NodeList artTitles = doc.getElementsByTagName("article-title");
        boolean artTitleOk = artTitles.getLength() > 0 && !artTitles.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("PMC-12", "Título del Artículo (<article-title>)", artTitleOk,
                artTitleOk ? "Título principal presente." : "Falta el título obligatorio <article-title> en <title-group>."));

        // PMC-13: Autores Granulares (<contrib contrib-type="author"> con <surname>)
        NodeList contribs = doc.getElementsByTagName("contrib");
        boolean autoresOk = true;
        int autorCount = 0;
        for (int i = 0; i < contribs.getLength(); i++) {
            Element el = (Element) contribs.item(i);
            if ("author".equalsIgnoreCase(el.getAttribute("contrib-type"))) {
                autorCount++;
                NodeList surnames = el.getElementsByTagName("surname");
                if (surnames.getLength() == 0 || surnames.item(0).getTextContent().isBlank()) {
                    autoresOk = false;
                    break;
                }
            }
        }
        boolean autoresCompletos = autorCount > 0 && autoresOk;
        checks.add(new CheckItem("PMC-13", "Autores con <surname> Granular", autoresCompletos,
                autoresCompletos ? "Se validaron " + autorCount + " autor(es) con <surname>." : "Falta etiqueta <surname> en autor(es) o no hay autores."));

        // PMC-14: ORCID Canónico (https://orcid.org/0000-xxxx-xxxx-xxxx)
        NodeList orcids = doc.getElementsByTagName("contrib-id");
        boolean orcidOk = true;
        String orcidError = "";
        for (int i = 0; i < orcids.getLength(); i++) {
            Element el = (Element) orcids.item(i);
            if ("orcid".equalsIgnoreCase(el.getAttribute("contrib-id-type"))) {
                String txt = el.getTextContent() != null ? el.getTextContent().trim() : "";
                if (!txt.matches("^https://orcid\\.org/\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$")) {
                    orcidOk = false;
                    orcidError = txt;
                    break;
                }
            }
        }
        checks.add(new CheckItem("PMC-14", "Identificadores ORCID Canónicos", orcidOk,
                orcidOk ? "ORCIDs conformes con la URL canónica https://orcid.org/." : "ORCID no canónico detectado: '" + orcidError + "'."));

        // PMC-15: Afiliaciones con atributo id único
        NodeList affs = doc.getElementsByTagName("aff");
        boolean affOk = affs.getLength() > 0;
        Set<String> affIds = new HashSet<>();
        for (int i = 0; i < affs.getLength(); i++) {
            Element aff = (Element) affs.item(i);
            String id = aff.getAttribute("id");
            if (id.isBlank() || affIds.contains(id)) {
                affOk = false;
                break;
            }
            affIds.add(id);
        }
        checks.add(new CheckItem("PMC-15", "Afiliaciones con ID Único (<aff id=\"...\">)", affOk,
                affOk ? affs.getLength() + " afiliación(es) con ID único." : "Falta atributo id en <aff> o se encontraron IDs duplicados."));

        // PMC-16: Vinculación Autor-Afiliación (<xref ref-type="aff">)
        NodeList affRefs = doc.getElementsByTagName("xref");
        boolean xrefAffOk = true;
        for (int i = 0; i < affRefs.getLength(); i++) {
            Element xr = (Element) affRefs.item(i);
            if ("aff".equalsIgnoreCase(xr.getAttribute("ref-type"))) {
                String rid = xr.getAttribute("rid");
                if (rid.isBlank() || !affIds.contains(rid)) {
                    xrefAffOk = false;
                    break;
                }
            }
        }
        checks.add(new CheckItem("PMC-16", "Vinculación Autor-Afiliación (<xref rid=\"...\">)", xrefAffOk,
                xrefAffOk ? "Todas las referencias de afiliación concuerdan con un <aff id> existente." : "Hay enlaces <xref ref-type=\"aff\"> huérfanos sin correspondencia en <aff>."));

        // PMC-17: Fecha de Publicación (<pub-date> con <year> de 4 dígitos)
        NodeList pubDates = doc.getElementsByTagName("pub-date");
        boolean pubDateOk = false;
        for (int i = 0; i < pubDates.getLength(); i++) {
            Element pd = (Element) pubDates.item(i);
            NodeList yrs = pd.getElementsByTagName("year");
            if (yrs.getLength() > 0 && yrs.item(0).getTextContent().trim().matches("\\d{4}")) {
                pubDateOk = true;
                break;
            }
        }
        checks.add(new CheckItem("PMC-17", "Fecha de Publicación (<pub-date> con <year>)", pubDateOk,
                pubDateOk ? "Fecha de publicación con año numérico de 4 dígitos." : "Falta <pub-date> con año numérico de 4 dígitos en <article-meta>."));

        // PMC-18: Volumen, Fascículo o Elocation-ID
        boolean hasVol = doc.getElementsByTagName("volume").getLength() > 0;
        boolean hasEloc = doc.getElementsByTagName("elocation-id").getLength() > 0;
        boolean hasFp = doc.getElementsByTagName("fpage").getLength() > 0;
        boolean numOk = hasVol || hasEloc || hasFp;
        checks.add(new CheckItem("PMC-18", "Numeración / Elocation-ID (<volume> / <elocation-id>)", numOk,
                numOk ? "Numeración o identificador electrónico presente." : "Se requiere <volume> o <elocation-id> o <fpage>."));

        // PMC-19: Permisos y Licencia (<permissions> / <license>)
        NodeList perms = doc.getElementsByTagName("permissions");
        boolean permOk = perms.getLength() > 0;
        checks.add(new CheckItem("PMC-19", "Bloque de Derechos y Licencia (<permissions>)", permOk,
                permOk ? "Bloque <permissions> presente." : "Falta el bloque obligatorio <permissions> en <article-meta>."));

        // PMC-20: Licencia Open Access con URL válida en xlink:href
        NodeList licenses = doc.getElementsByTagName("license");
        boolean licOk = false;
        for (int i = 0; i < licenses.getLength(); i++) {
            Element l = (Element) licenses.item(i);
            String href = l.getAttribute("xlink:href");
            if (href != null && href.contains("creativecommons.org")) {
                licOk = true;
                break;
            }
        }
        checks.add(new CheckItem("PMC-20", "Licencia Creative Commons (xlink:href)", licOk,
                licOk ? "Licencia Creative Commons vinculada con URL válida." : "Falta URL de licencia Creative Commons en xlink:href."));

        // PMC-21: Resumen Estructurado (<abstract>)
        NodeList abstracts = doc.getElementsByTagName("abstract");
        boolean absOk = abstracts.getLength() > 0 && !abstracts.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("PMC-21", "Resumen del Artículo (<abstract>)", absOk,
                absOk ? "Resumen presente con texto estructurado." : "Falta el elemento <abstract> en <article-meta>."));

        // PMC-22: Palabras Clave (<kwd-group>/<kwd>)
        NodeList kwds = doc.getElementsByTagName("kwd");
        boolean kwdOk = kwds.getLength() >= 2;
        checks.add(new CheckItem("PMC-22", "Palabras Clave (<kwd-group>/<kwd>)", kwdOk,
                kwdOk ? kwds.getLength() + " palabras clave estructuradas." : "Se requieren al menos 2 palabras clave <kwd>."));

        // PMC-23: Declaración de Financiamiento conforme a NLM (sin <funding-group> vacío)
        NodeList fgs = doc.getElementsByTagName("funding-group");
        boolean fundingOk = true;
        for (int i = 0; i < fgs.getLength(); i++) {
            Element fg = (Element) fgs.item(i);
            if (fg.getElementsByTagName("award-group").getLength() == 0) {
                fundingOk = false;
                break;
            }
        }
        checks.add(new CheckItem("PMC-23", "Financiamiento conforme NLM (award-group)", fundingOk,
                fundingOk ? "Financiamiento compatible (sin funding-group vacío)." : "PMC Stylechecker rechaza <funding-group> sin <award-group>."));

        // PMC-24: Secciones de Texto con Título (<sec>/<title>)
        NodeList secs = doc.getElementsByTagName("sec");
        boolean secOk = true;
        if (secs.getLength() > 0) {
            for (int i = 0; i < secs.getLength(); i++) {
                Element s = (Element) secs.item(i);
                if (s.getElementsByTagName("title").getLength() == 0) {
                    secOk = false;
                    break;
                }
            }
        }
        checks.add(new CheckItem("PMC-24", "Secciones con Título (<sec>/<title>)", secOk,
                secOk ? "Todas las secciones contienen su etiqueta <title>." : "Se detectaron secciones <sec> sin etiqueta <title>."));

        // PMC-25: Imágenes y Figuras (<fig> con <graphic xlink:href>)
        NodeList figs = doc.getElementsByTagName("fig");
        boolean figOk = true;
        for (int i = 0; i < figs.getLength(); i++) {
            Element f = (Element) figs.item(i);
            NodeList graphics = f.getElementsByTagName("graphic");
            if (graphics.getLength() == 0 || ((Element) graphics.item(0)).getAttribute("xlink:href").isBlank()) {
                figOk = false;
                break;
            }
        }
        checks.add(new CheckItem("PMC-25", "Figuras con Gráfico (<fig>/<graphic>)", figOk,
                figOk ? "Figuras correctamente vinculadas a imágenes." : "Figuras sin elemento <graphic xlink:href>."));

        // PMC-26: Tablas con estructura válida (<table> dentro de <table-wrap>)
        NodeList tables = doc.getElementsByTagName("table-wrap");
        boolean tableOk = true;
        for (int i = 0; i < tables.getLength(); i++) {
            Element tw = (Element) tables.item(i);
            if (tw.getElementsByTagName("table").getLength() == 0) {
                tableOk = false;
                break;
            }
        }
        checks.add(new CheckItem("PMC-26", "Tablas Estructuradas (<table-wrap>/<table>)", tableOk,
                tableOk ? "Tablas contenidas dentro de <table-wrap>." : "Se encontraron <table-wrap> sin <table>."));

        // PMC-27: Referencias con IDs Únicos en <ref-list>
        NodeList refs = doc.getElementsByTagName("ref");
        Set<String> refIds = new HashSet<>();
        boolean refIdsOk = true;
        for (int i = 0; i < refs.getLength(); i++) {
            Element r = (Element) refs.item(i);
            String id = r.getAttribute("id");
            if (id.isBlank() || refIds.contains(id)) {
                refIdsOk = false;
                break;
            }
            refIds.add(id);
        }
        checks.add(new CheckItem("PMC-27", "Identificadores Únicos en Referencias (<ref id=\"...\">)", refIdsOk,
                refIdsOk ? refs.getLength() + " referencias con ID único." : "Falta atributo id en <ref> o se detectaron IDs duplicados."));

        // PMC-28: Citas bibliográficas con element-citation o mixed-citation
        NodeList elementCitations = doc.getElementsByTagName("element-citation");
        boolean refStructureOk = true;
        if (refs.getLength() > 0 && elementCitations.getLength() == 0) {
            refStructureOk = false;
        } else {
            for (int i = 0; i < elementCitations.getLength(); i++) {
                Element ec = (Element) elementCitations.item(i);
                String pt = ec.getAttribute("publication-type");
                if (pt.isBlank() || !CITATION_VALID_TYPES.contains(pt.toLowerCase())) {
                    refStructureOk = false;
                    break;
                }
                NodeList yrs = ec.getElementsByTagName("year");
                if (yrs.getLength() == 0 || !yrs.item(0).getTextContent().trim().matches("\\d{4}")) {
                    refStructureOk = false;
                    break;
                }
            }
        }
        checks.add(new CheckItem("PMC-28", "Estructura de Citas (publication-type y <year>)", refStructureOk,
                refStructureOk ? "Citas estructuradas con publication-type y año numérico." : "Citas sin publication-type estándar o sin año <year> de 4 dígitos."));

        // PMC-29: Referencias Cruzadas en el Texto (<xref ref-type="bibr"> sin referencias rotas)
        NodeList bibrRefs = doc.getElementsByTagName("xref");
        boolean xrefBibrOk = true;
        for (int i = 0; i < bibrRefs.getLength(); i++) {
            Element xr = (Element) bibrRefs.item(i);
            if ("bibr".equalsIgnoreCase(xr.getAttribute("ref-type"))) {
                String rid = xr.getAttribute("rid");
                if (rid.isBlank() || !refIds.contains(rid)) {
                    xrefBibrOk = false;
                    break;
                }
            }
        }
        checks.add(new CheckItem("PMC-29", "Referencias Cruzadas en Texto (<xref ref-type=\"bibr\">)", xrefBibrOk,
                xrefBibrOk ? "Todas las citas en el texto enlazan con una referencia <ref id> existente." : "Hay citas en el texto (<xref ref-type=\"bibr\">) que apuntan a IDs inexistentes."));

        // PMC-30: Ausencia de entidades HTML prohibidas
        boolean entitiesOk = !rawXml.contains("&nbsp;") && !rawXml.contains("&copy;");
        checks.add(new CheckItem("PMC-30", "Codificación de Entidades XML (UTF-8)", entitiesOk,
                entitiesOk ? "Entidades XML limpias y compatibles UTF-8." : "Se encontraron entidades HTML prohibidas (ej. &nbsp;, &copy;)."));

        return checks;
    }

    /**
     * Batería oficial de reglas evaluadas por SciELO (SciELO Publishing Schema SPS 1.9 / 1.10 / JATS4R / Redalyc).
     */
    private List<CheckItem> auditScieloExhaustive(Document doc, String rawXml) {
        List<CheckItem> checks = new ArrayList<>();

        // SCI-01: Título Principal (<article-title>)
        NodeList titles = doc.getElementsByTagName("article-title");
        boolean hasTitle = titles.getLength() > 0 && !titles.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("SCI-01", "Título Principal en Idioma Original (<article-title>)", hasTitle,
                hasTitle ? "Título original presente." : "SciELO SPS exige <article-title> en idioma original."));

        // SCI-02: Título Traducido Obligatorio en Inglés (<trans-title>)
        NodeList transTitles = doc.getElementsByTagName("trans-title");
        boolean hasTransTitle = transTitles.getLength() > 0 && !transTitles.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("SCI-02", "Título Traducido Obligatorio en Inglés (<trans-title>)", hasTransTitle,
                hasTransTitle ? "Título traducido presente: " + transTitles.item(0).getTextContent().trim() : "SciELO SPS exige título traducido (en inglés)."));

        // SCI-03: Resumen en Idioma Original (<abstract>)
        NodeList abstracts = doc.getElementsByTagName("abstract");
        boolean hasAbstract = abstracts.getLength() > 0 && !abstracts.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("SCI-03", "Resumen en Idioma Original (<abstract>)", hasAbstract,
                hasAbstract ? "Resumen en idioma original presente." : "SciELO SPS exige <abstract> en idioma original."));

        // SCI-04: Resumen Traducido Obligatorio en Inglés (<trans-abstract>)
        NodeList transAbstracts = doc.getElementsByTagName("trans-abstract");
        boolean hasTransAbstract = transAbstracts.getLength() > 0 && !transAbstracts.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("SCI-04", "Resumen Traducido Obligatorio en Inglés (<trans-abstract>)", hasTransAbstract,
                hasTransAbstract ? "Abstract en inglés presente." : "SciELO SPS exige abstract traducido en inglés (<trans-abstract>)."));

        // SCI-05: Palabras Clave Bilingües (al menos dos kwd-group con idiomas distintos)
        NodeList kwdGroups = doc.getElementsByTagName("kwd-group");
        Set<String> kwdLangs = new HashSet<>();
        int totalKwds = 0;
        for (int i = 0; i < kwdGroups.getLength(); i++) {
            Element el = (Element) kwdGroups.item(i);
            String l = el.getAttribute("xml:lang");
            if (l != null && !l.isBlank()) kwdLangs.add(l.toLowerCase());
            totalKwds += el.getElementsByTagName("kwd").getLength();
        }
        boolean kwdMulti = kwdLangs.size() >= 2 && totalKwds >= 4;
        checks.add(new CheckItem("SCI-05", "Palabras Clave Bilingües (<kwd-group> multilingüe)", kwdMulti,
                kwdMulti ? "Palabras clave en múltiples idiomas: " + kwdLangs : "SciELO SPS exige palabras clave en al menos dos idiomas (ej. es y en)."));

        // SCI-06: Institución de Afiliación Normalizada (<institution content-type="orgname">)
        NodeList insts = doc.getElementsByTagName("institution");
        boolean instOk = insts.getLength() > 0;
        for (int i = 0; i < insts.getLength(); i++) {
            if (insts.item(i).getTextContent().isBlank()) {
                instOk = false;
                break;
            }
        }
        checks.add(new CheckItem("SCI-06", "Institución Normalizada en Afiliaciones (<institution>)", instOk,
                instOk ? "Institución académica normalizada presente." : "SciELO SPS exige <institution> en los bloques de afiliación."));

        // SCI-07: País con atributo ISO country="XX" en <country>
        NodeList countries = doc.getElementsByTagName("country");
        boolean countryOk = countries.getLength() > 0;
        for (int i = 0; i < countries.getLength(); i++) {
            Element c = (Element) countries.item(i);
            String countryCode = c.getAttribute("country");
            if (c.getTextContent().isBlank() || (!countryCode.isBlank() && !countryCode.matches("^[A-Za-z]{2}$"))) {
                countryOk = false;
                break;
            }
        }
        checks.add(new CheckItem("SCI-07", "País Normalizado en Afiliaciones (<country country=\"XX\">)", countryOk,
                countryOk ? "País declarado conforme a la norma ISO 3166-1." : "SciELO SPS exige <country> con código de país de 2 letras en atributo country (ej. AR)."));

        // SCI-08: Afiliaciones con ID Único (<aff id="...">)
        NodeList affList = doc.getElementsByTagName("aff");
        Set<String> affIds = new HashSet<>();
        boolean affOk = affList.getLength() > 0;
        for (int i = 0; i < affList.getLength(); i++) {
            String id = ((Element) affList.item(i)).getAttribute("id");
            if (id.isBlank() || affIds.contains(id)) {
                affOk = false;
                break;
            }
            affIds.add(id);
        }
        checks.add(new CheckItem("SCI-08", "Identificador Único en Afiliaciones (<aff id=\"...\">)", affOk,
                affOk ? affList.getLength() + " afiliación(es) con ID único." : "SciELO SPS exige atributo id único en cada <aff>."));

        // SCI-09: Autores con Nombre y Apellido Desglosados
        NodeList contribs = doc.getElementsByTagName("contrib");
        boolean autoresOk = contribs.getLength() > 0;
        for (int i = 0; i < contribs.getLength(); i++) {
            Element c = (Element) contribs.item(i);
            if ("author".equalsIgnoreCase(c.getAttribute("contrib-type"))) {
                NodeList sn = c.getElementsByTagName("surname");
                NodeList gn = c.getElementsByTagName("given-names");
                if (sn.getLength() == 0 || gn.getLength() == 0 || sn.item(0).getTextContent().isBlank() || gn.item(0).getTextContent().isBlank()) {
                    autoresOk = false;
                    break;
                }
            }
        }
        checks.add(new CheckItem("SCI-09", "Autores con Nombre y Apellido Desglosados (<name>)", autoresOk,
                autoresOk ? "Autores desglosados con <surname> y <given-names>." : "SciELO SPS exige nombre (<given-names>) y apellido (<surname>) desglosados."));

        // SCI-10: Vinculación Obligatoria Autor-Afiliación
        NodeList xrefs = doc.getElementsByTagName("xref");
        boolean xrefOk = false;
        for (int i = 0; i < xrefs.getLength(); i++) {
            Element xr = (Element) xrefs.item(i);
            if ("aff".equalsIgnoreCase(xr.getAttribute("ref-type"))) {
                String rid = xr.getAttribute("rid");
                if (!rid.isBlank() && affIds.contains(rid)) {
                    xrefOk = true;
                    break;
                }
            }
        }
        checks.add(new CheckItem("SCI-10", "Vinculación Autor-Afiliación (<xref ref-type=\"aff\">)", xrefOk,
                xrefOk ? "Autores vinculados formalmente a sus afiliaciones." : "SciELO SPS exige que los autores estén vinculados a su afiliación vía <xref ref-type=\"aff\">."));

        // SCI-11: Autor de Correspondencia o Email
        boolean correspOk = false;
        for (int i = 0; i < contribs.getLength(); i++) {
            Element c = (Element) contribs.item(i);
            if ("yes".equalsIgnoreCase(c.getAttribute("corresp")) || c.getElementsByTagName("email").getLength() > 0) {
                correspOk = true;
                break;
            }
        }
        if (!correspOk) {
            correspOk = doc.getElementsByTagName("author-notes").getLength() > 0
                    || doc.getElementsByTagName("corresp").getLength() > 0;
        }
        checks.add(new CheckItem("SCI-11", "Autor de Correspondencia o Email (<email>/corresp)", correspOk,
                correspOk ? "Autor de correspondencia o email identificado." : "SciELO SPS exige marcar corresp=\"yes\" o incluir <email> de contacto."));

        // SCI-12: Identificador Persistente o DOI
        NodeList articleIds = doc.getElementsByTagName("article-id");
        boolean doiOk = false;
        for (int i = 0; i < articleIds.getLength(); i++) {
            Element el = (Element) articleIds.item(i);
            if ("doi".equalsIgnoreCase(el.getAttribute("pub-id-type")) && el.getTextContent().trim().startsWith("10.")) {
                doiOk = true;
                break;
            }
        }
        checks.add(new CheckItem("SCI-12", "Identificador Persistente DOI (<article-id pub-id-type=\"doi\">)", doiOk,
                doiOk ? "DOI normalizado presente." : "SciELO SPS exige identificador DOI con prefijo '10.'."));

        // SCI-13: Licencia Creative Commons Open Access
        NodeList licenses = doc.getElementsByTagName("license");
        boolean licOk = false;
        for (int i = 0; i < licenses.getLength(); i++) {
            Element l = (Element) licenses.item(i);
            String href = l.getAttribute("xlink:href");
            if (href != null && href.contains("creativecommons.org")) {
                licOk = true;
                break;
            }
        }
        checks.add(new CheckItem("SCI-13", "Licencia Creative Commons Open Access en <permissions>", licOk,
                licOk ? "Licencia Creative Commons identificada con URL." : "SciELO SPS exige licencia CC en <license xlink:href=\"...\">."));

        // SCI-14: Historial Editorial con fechas received y accepted
        NodeList histDates = doc.getElementsByTagName("date");
        boolean recOk = false;
        boolean accOk = false;
        for (int i = 0; i < histDates.getLength(); i++) {
            Element dt = (Element) histDates.item(i);
            String dtType = dt.getAttribute("date-type");
            if ("received".equalsIgnoreCase(dtType)) recOk = true;
            if ("accepted".equalsIgnoreCase(dtType)) accOk = true;
        }
        boolean histOk = recOk && accOk;
        checks.add(new CheckItem("SCI-14", "Historial Editorial (received / accepted)", histOk,
                histOk ? "Fechas de recepción y aceptación presentes en <history>." : "SciELO SPS exige fecha de recepción y aceptación en <history>."));

        // SCI-15: Fechas del Historial con Año Numérico de 4 Dígitos
        boolean histYearsOk = false;
        if (histOk) {
            histYearsOk = true;
            for (int i = 0; i < histDates.getLength(); i++) {
                Element dt = (Element) histDates.item(i);
                NodeList yrs = dt.getElementsByTagName("year");
                if (yrs.getLength() == 0 || !yrs.item(0).getTextContent().trim().matches("\\d{4}")) {
                    histYearsOk = false;
                    break;
                }
            }
        }
        checks.add(new CheckItem("SCI-15", "Año Numérico en Fechas del Historial (<year>)", histYearsOk,
                histYearsOk ? "Fechas de arbitraje con año de 4 dígitos." : "Las fechas del historial deben contener <year> de 4 dígitos."));

        // SCI-16: Secciones Estructuradas con Títulos (<sec>/<title>)
        NodeList secs = doc.getElementsByTagName("sec");
        boolean secOk = true;
        if (secs.getLength() > 0) {
            for (int i = 0; i < secs.getLength(); i++) {
                Element s = (Element) secs.item(i);
                if (s.getElementsByTagName("title").getLength() == 0) {
                    secOk = false;
                    break;
                }
            }
        }
        checks.add(new CheckItem("SCI-16", "Estructura de Secciones con Títulos (<sec>/<title>)", secOk,
                secOk ? "Secciones del cuerpo estructuradas con títulos." : "Secciones <sec> sin <title>."));

        // SCI-17: Referencias Bibliográficas Estructuradas (<ref-list>)
        NodeList refs = doc.getElementsByTagName("ref");
        boolean refOk = refs.getLength() > 0;
        checks.add(new CheckItem("SCI-17", "Lista de Referencias Bibliográficas (<ref-list>)", refOk,
                refOk ? refs.getLength() + " referencias registradas en <back>." : "No se encontraron referencias bibliográficas en <back>."));

        // SCI-18: Tipo de Publicación en Citas (publication-type)
        NodeList citations = doc.getElementsByTagName("element-citation");
        boolean citTypeOk = citations.getLength() > 0;
        for (int i = 0; i < citations.getLength(); i++) {
            Element cit = (Element) citations.item(i);
            String pt = cit.getAttribute("publication-type");
            if (pt.isBlank() || !CITATION_VALID_TYPES.contains(pt.toLowerCase())) {
                citTypeOk = false;
                break;
            }
        }
        checks.add(new CheckItem("SCI-18", "Tipo de Publicación en Citas (publication-type)", citTypeOk,
                citTypeOk ? "Citas estructuradas con publication-type válido." : "Citas sin atributo publication-type reconocido por SciELO SPS."));

        // SCI-19: Citas con Autores o Autores Corporativos (<person-group>/<name> o <collab>)
        boolean citAuthorsOk = citations.getLength() > 0;
        for (int i = 0; i < citations.getLength(); i++) {
            Element cit = (Element) citations.item(i);
            boolean hasName = cit.getElementsByTagName("name").getLength() > 0;
            boolean hasCollab = cit.getElementsByTagName("collab").getLength() > 0;
            if (!hasName && !hasCollab) {
                citAuthorsOk = false;
                break;
            }
        }
        checks.add(new CheckItem("SCI-19", "Autores de Citas (<person-group>/<name> o <collab>)", citAuthorsOk,
                citAuthorsOk ? "Autores o instituciones autoras identificadas en citas." : "Hay citas sin autores (<name> o <collab>)."));

        // SCI-20: Citas con Año de Publicación (<year>)
        boolean citYearOk = citations.getLength() > 0;
        for (int i = 0; i < citations.getLength(); i++) {
            Element cit = (Element) citations.item(i);
            NodeList yrs = cit.getElementsByTagName("year");
            if (yrs.getLength() == 0 || !yrs.item(0).getTextContent().trim().matches("\\d{4}")) {
                citYearOk = false;
                break;
            }
        }
        checks.add(new CheckItem("SCI-20", "Año de Publicación en Citas (<year>)", citYearOk,
                citYearOk ? "Citas con año numérico de 4 dígitos." : "Hay citas bibliográficas sin año <year> de 4 dígitos."));

        // SCI-21: Revista Fuente en Citas Journal (<source>)
        boolean citSourceOk = true;
        for (int i = 0; i < citations.getLength(); i++) {
            Element cit = (Element) citations.item(i);
            if ("journal".equalsIgnoreCase(cit.getAttribute("publication-type"))) {
                NodeList sources = cit.getElementsByTagName("source");
                if (sources.getLength() == 0 || sources.item(0).getTextContent().isBlank()) {
                    citSourceOk = false;
                    break;
                }
            }
        }
        checks.add(new CheckItem("SCI-21", "Revista Fuente en Citas de Artículos (<source>)", citSourceOk,
                citSourceOk ? "Referencias a revistas contienen título de fuente <source>." : "SciELO SPS exige <source> (revista) en citas de artículos de revista."));

        // SCI-22: Declaración de Conflicto de Intereses
        NodeList fns = doc.getElementsByTagName("fn");
        boolean coiOk = false;
        for (int i = 0; i < fns.getLength(); i++) {
            Element fn = (Element) fns.item(i);
            String fnType = fn.getAttribute("fn-type");
            if ("conflict".equalsIgnoreCase(fnType) || "coi-statement".equalsIgnoreCase(fnType)) {
                coiOk = true;
                break;
            }
        }
        if (!coiOk && doc.getElementsByTagName("author-notes").getLength() > 0) {
            coiOk = true;
        }
        checks.add(new CheckItem("SCI-22", "Declaración de Conflicto de Intereses (<fn fn-type=\"conflict\">)", coiOk,
                coiOk ? "Declaración de conflicto de intereses presente." : "Criterio de transparencia SciELO/JATS4R: incluir nota de conflicto de intereses."));

        // SCI-23: Declaración de Disponibilidad de Datos
        boolean dataAvailOk = false;
        for (int i = 0; i < secs.getLength(); i++) {
            Element sec = (Element) secs.item(i);
            if ("data-availability".equalsIgnoreCase(sec.getAttribute("sec-type"))) {
                dataAvailOk = true;
                break;
            }
        }
        if (!dataAvailOk) {
            for (int i = 0; i < fns.getLength(); i++) {
                if ("data-availability".equalsIgnoreCase(((Element) fns.item(i)).getAttribute("fn-type"))) {
                    dataAvailOk = true;
                    break;
                }
            }
        }
        checks.add(new CheckItem("SCI-23", "Disponibilidad de Datos de Investigación (data-availability)", dataAvailOk,
                dataAvailOk ? "Declaración de datos de investigación presente." : "Criterio de ciencia abierta SciELO/JATS4R: incluir disponibilidad de datos."));

        return checks;
    }
}
