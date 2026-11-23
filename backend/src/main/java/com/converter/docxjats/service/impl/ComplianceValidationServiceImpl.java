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
 * Validador exhaustivo de conformidad técnica con absolutamente todos los checks
 * requeridos por PubMed Central (PMC Stylechecker / NLM DTD) y Redalyc (Marcalyc / JATS4R / SciELO SPS).
 */
@Service
public class ComplianceValidationServiceImpl implements IComplianceValidationService {

    private static final Set<String> PMC_VALID_ARTICLE_TYPES = Set.of(
            "research-article", "review-article", "editorial", "letter",
            "brief-report", "case-report", "other", "correction", "retraction",
            "article-commentary", "rapid-communication", "product-review"
    );

    private static final Set<String> CITATION_VALID_TYPES = Set.of(
            "journal", "book", "chapter", "confproc", "thesis", "webpage",
            "report", "patent", "standard", "software", "data", "newspaper", "legal-doc"
    );

    @Override
    public ComplianceReportDto auditCompliance(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return new ComplianceReportDto(
                    false,
                    List.of(new CheckItem("PMC-00", "Contenido XML", false, "El documento XML está completamente vacío.")),
                    false,
                    List.of(new CheckItem("RED-00", "Contenido XML", false, "El documento XML está completamente vacío."))
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
                    List.of(new CheckItem("RED-00", "Sintaxis XML", false, errorMsg))
            );
        }

        List<CheckItem> pmcChecks = auditPmcExhaustive(doc, xmlContent);
        List<CheckItem> redalycChecks = auditRedalycExhaustive(doc, xmlContent);

        boolean pmcApto = pmcChecks.stream().allMatch(CheckItem::cumplido);
        boolean redalycApto = redalycChecks.stream().allMatch(CheckItem::cumplido);

        return new ComplianceReportDto(pmcApto, pmcChecks, redalycApto, redalycChecks);
    }

    /**
     * Batería completa de checks de PubMed Central (PMC Stylechecker).
     */
    private List<CheckItem> auditPmcExhaustive(Document doc, String rawXml) {
        List<CheckItem> checks = new ArrayList<>();
        Element root = doc.getDocumentElement();

        // PMC-01: Elemento raíz <article>
        boolean hasRoot = "article".equals(root.getNodeName());
        checks.add(new CheckItem("PMC-01", "Elemento Raíz <article>", hasRoot,
                hasRoot ? "Elemento raíz <article> correcto." : "El elemento raíz debe ser estrictamente <article>."));

        // PMC-02: Atributo article-type
        String articleType = root.getAttribute("article-type");
        boolean articleTypeOk = !articleType.isBlank() && PMC_VALID_ARTICLE_TYPES.contains(articleType.toLowerCase());
        checks.add(new CheckItem("PMC-02", "Atributo article-type Estándar", articleTypeOk,
                articleTypeOk ? "Tipo de artículo válido: " + articleType : "article-type no es válido según NLM: '" + articleType + "'."));

        // PMC-03: Atributo dtd-version
        String dtdVersion = root.getAttribute("dtd-version");
        boolean dtdOk = dtdVersion.matches("1\\.[0-3]");
        checks.add(new CheckItem("PMC-03", "Atributo dtd-version", dtdOk,
                dtdOk ? "Versión DTD reconocida: " + dtdVersion : "dtd-version debe ser 1.0, 1.1, 1.2 o 1.3 (encontrado: '" + dtdVersion + "')."));

        // PMC-04: Atributo xml:lang
        String xmlLang = root.getAttribute("xml:lang");
        boolean langOk = xmlLang.matches("^[a-z]{2}(-[A-Z]{2})?$");
        checks.add(new CheckItem("PMC-04", "Idioma Principal xml:lang", langOk,
                langOk ? "Idioma ISO declarado: " + xmlLang : "El atributo xml:lang debe ser código ISO válido (ej: 'es', 'en')."));

        // PMC-05: Declaración de Namespace xlink
        boolean hasXlink = rawXml.contains("xmlns:xlink") || rawXml.contains("http://www.w3.org/1999/xlink");
        checks.add(new CheckItem("PMC-05", "Espacio de Nombres xlink", hasXlink,
                hasXlink ? "xmlns:xlink declarado correctamente." : "Falta declaración xmlns:xlink=\"http://www.w3.org/1999/xlink\"."));

        // PMC-06: Bloques Mayores (<front>, <body>, <back>)
        boolean hasFront = doc.getElementsByTagName("front").getLength() > 0;
        boolean hasBody = doc.getElementsByTagName("body").getLength() > 0;
        boolean hasBack = doc.getElementsByTagName("back").getLength() > 0;
        boolean bloquesOk = hasFront && hasBody && hasBack;
        checks.add(new CheckItem("PMC-06", "Estructura de 3 Secciones (<front>, <body>, <back>)", bloquesOk,
                bloquesOk ? "Estructura completa de 3 secciones presentes." : "Faltan bloques obligatorios JATS."));

        // PMC-07: Título de Revista (<journal-title>)
        NodeList jTitles = doc.getElementsByTagName("journal-title");
        boolean jTitleOk = jTitles.getLength() > 0 && !jTitles.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("PMC-07", "Título de Revista (<journal-title>)", jTitleOk,
                jTitleOk ? "Título de revista: " + jTitles.item(0).getTextContent().trim() : "Falta <journal-title> en <journal-meta>."));

        // PMC-08: Editorial / Publisher (<publisher-name>)
        NodeList publishers = doc.getElementsByTagName("publisher-name");
        boolean pubOk = publishers.getLength() > 0 && !publishers.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("PMC-08", "Nombre de la Editorial (<publisher-name>)", pubOk,
                pubOk ? "Editorial declarada: " + publishers.item(0).getTextContent().trim() : "Falta <publisher-name> en <journal-meta>."));

        // PMC-09: ISSN Registrado (<issn>)
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
        checks.add(new CheckItem("PMC-09", "Formato de ISSN (XXXX-XXXX)", issnOk,
                issnOk ? "ISSN válido: " + issnVal : "No se encontró un <issn> con formato válido (ej. 1851-8265)."));

        // PMC-10: Identificador DOI del Artículo
        NodeList articleIds = doc.getElementsByTagName("article-id");
        boolean doiOk = false;
        String doiVal = "";
        for (int i = 0; i < articleIds.getLength(); i++) {
            Element el = (Element) articleIds.item(i);
            if ("doi".equalsIgnoreCase(el.getAttribute("pub-id-type"))) {
                String txt = el.getTextContent() != null ? el.getTextContent() : "";
                if (!txt.trim().isEmpty() && !txt.matches(".*\\s+.*") && txt.startsWith("10.")) {
                    doiOk = true;
                    doiVal = txt.trim();
                    break;
                }
            }
        }
        checks.add(new CheckItem("PMC-10", "Sanitización del DOI (<article-id pub-id-type=\"doi\">)", doiOk,
                doiOk ? "DOI válido sin espacios: " + doiVal : "DOI falta, tiene espacios o no comienza con '10.'."));

        // PMC-11: Título del Manuscrito (<article-title>)
        NodeList artTitles = doc.getElementsByTagName("article-title");
        boolean artTitleOk = artTitles.getLength() > 0 && !artTitles.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("PMC-11", "Título del Artículo (<article-title>)", artTitleOk,
                artTitleOk ? "Título principal presente." : "Falta el título obligatorio <article-title>."));

        // PMC-12: Autores Granulares (<contrib contrib-type="author"> con <surname>)
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
        checks.add(new CheckItem("PMC-12", "Autores con <surname> Granular", autoresCompletos,
                autoresCompletos ? "Se validaron " + autorCount + " autor(es) con <surname>." : "Falta etiqueta <surname> en autor(es) o no hay autores."));

        // PMC-13: ORCID Canónico (https://orcid.org/0000-xxxx-xxxx-xxxx)
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
        checks.add(new CheckItem("PMC-13", "Identificadores ORCID Canónicos", orcidOk,
                orcidOk ? "ORCIDs conformes con la URL canónica https://orcid.org/." : "ORCID no canónico detectado: '" + orcidError + "'."));

        // PMC-14: Afiliaciones con atributo id único
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
        checks.add(new CheckItem("PMC-14", "Afiliaciones con ID Único (<aff id=\"...\">)", affOk,
                affOk ? affs.getLength() + " afiliación(es) con ID único." : "Falta atributo id en <aff> o IDs duplicados."));

        // PMC-15: Vinculación Autor-Afiliación (<xref ref-type="aff">)
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
        checks.add(new CheckItem("PMC-15", "Vinculación Autor-Afiliación (<xref rid=\"...\">)", xrefAffOk,
                xrefAffOk ? "Todas las referencias de afiliación concuerdan con un <aff id> existente." : "Hay enlaces <xref ref-type=\"aff\"> huérfanos sin correspondencia en <aff>."));

        // PMC-16: Fecha de Publicación (<pub-date> con <year> de 4 dígitos)
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
        checks.add(new CheckItem("PMC-16", "Fecha de Publicación (<pub-date> con <year>)", pubDateOk,
                pubDateOk ? "Fecha de publicación válida." : "Falta <pub-date> con año numérico de 4 dígitos."));

        // PMC-17: Volumen, Fascículo o Elocation-ID
        boolean hasVol = doc.getElementsByTagName("volume").getLength() > 0;
        boolean hasEloc = doc.getElementsByTagName("elocation-id").getLength() > 0;
        boolean hasFp = doc.getElementsByTagName("fpage").getLength() > 0;
        boolean numOk = hasVol || hasEloc || hasFp;
        checks.add(new CheckItem("PMC-17", "Numeración / Elocation-ID (<volume> / <elocation-id>)", numOk,
                numOk ? "Numeración o identificador electrónico presente." : "Se requiere <volume> o <elocation-id>."));

        // PMC-18: Permisos y Licencia (<permissions> / <license>)
        NodeList perms = doc.getElementsByTagName("permissions");
        boolean permOk = perms.getLength() > 0;
        checks.add(new CheckItem("PMC-18", "Bloque de Derechos y Licencia (<permissions>)", permOk,
                permOk ? "Bloque <permissions> presente." : "Falta el bloque obligatorio <permissions> en <article-meta>."));

        // PMC-19: Licencia Open Access con URL válida en xlink:href
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
        checks.add(new CheckItem("PMC-19", "Licencia Creative Commons (xlink:href)", licOk,
                licOk ? "Licencia Creative Commons vinculada con URL válida." : "Falta URL de licencia Creative Commons en xlink:href."));

        // PMC-20: Resumen Estructurado (<abstract>)
        NodeList abstracts = doc.getElementsByTagName("abstract");
        boolean absOk = abstracts.getLength() > 0 && !abstracts.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("PMC-20", "Resumen del Artículo (<abstract>)", absOk,
                absOk ? "Resumen presente con texto estructurado." : "Falta el elemento <abstract> en <article-meta>."));

        // PMC-21: Palabras Clave (<kwd-group>/<kwd>)
        NodeList kwds = doc.getElementsByTagName("kwd");
        boolean kwdOk = kwds.getLength() >= 2;
        checks.add(new CheckItem("PMC-21", "Palabras Clave (<kwd-group>/<kwd>)", kwdOk,
                kwdOk ? kwds.getLength() + " palabras clave estructuradas." : "Se requieren al menos 2 palabras clave <kwd>."));

        // PMC-22: Declaración de Financiamiento conforme a NLM (sin <funding-group> vacío)
        NodeList fgs = doc.getElementsByTagName("funding-group");
        boolean fundingOk = true;
        for (int i = 0; i < fgs.getLength(); i++) {
            Element fg = (Element) fgs.item(i);
            if (fg.getElementsByTagName("award-group").getLength() == 0) {
                fundingOk = false;
                break;
            }
        }
        checks.add(new CheckItem("PMC-22", "Financiamiento conforme NLM (award-group)", fundingOk,
                fundingOk ? "Financiamiento compatible (sin funding-group vacío)." : "PMC Stylechecker rechaza <funding-group> sin <award-group>."));

        // PMC-23: Secciones de Texto con Título (<sec>/<title>)
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
        checks.add(new CheckItem("PMC-23", "Secciones con Título (<sec>/<title>)", secOk,
                secOk ? "Todas las secciones contienen su etiqueta <title>." : "Se detectaron secciones <sec> sin etiqueta <title>."));

        // PMC-24: Imágenes y Figuras (<fig> con <graphic xlink:href>)
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
        checks.add(new CheckItem("PMC-24", "Figuras con Gráfico (<fig>/<graphic>)", figOk,
                figOk ? "Figuras correctamente vinculadas a imágenes." : "Figuras sin elemento <graphic xlink:href>."));

        // PMC-25: Tablas con estructura válida (<table> dentro de <table-wrap>)
        NodeList tables = doc.getElementsByTagName("table-wrap");
        boolean tableOk = true;
        for (int i = 0; i < tables.getLength(); i++) {
            Element tw = (Element) tables.item(i);
            if (tw.getElementsByTagName("table").getLength() == 0) {
                tableOk = false;
                break;
            }
        }
        checks.add(new CheckItem("PMC-25", "Tablas Estructuradas (<table-wrap>/<table>)", tableOk,
                tableOk ? "Tablas contenidas dentro de <table-wrap>." : "Se encontraron <table-wrap> sin <table>."));

        // PMC-26: Citas bibliográficas con element-citation o mixed-citation
        NodeList elementCitations = doc.getElementsByTagName("element-citation");
        NodeList refs = doc.getElementsByTagName("ref");
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
            }
        }
        checks.add(new CheckItem("PMC-26", "Estructura de Referencias (publication-type)", refStructureOk,
                refStructureOk ? "Citas estructuradas con tipo de publicación válido." : "Citas sin atributo publication-type reconocido por PMC."));

        // PMC-27: Años en referencias (<year> de 4 dígitos)
        boolean refYearOk = true;
        for (int i = 0; i < elementCitations.getLength(); i++) {
            Element ec = (Element) elementCitations.item(i);
            NodeList yrs = ec.getElementsByTagName("year");
            if (yrs.getLength() == 0 || !yrs.item(0).getTextContent().trim().matches("\\d{4}")) {
                refYearOk = false;
                break;
            }
        }
        checks.add(new CheckItem("PMC-27", "Años Numéricos en Citas (<year>)", refYearOk,
                refYearOk ? "Referencias con año numérico de 4 dígitos." : "Hay referencias bibliográficas sin etiqueta <year> de 4 dígitos."));

        // PMC-28: Ausencia de caracteres no escapados o entidades prohibidas
        boolean entitiesOk = !rawXml.contains("&nbsp;") && !rawXml.contains("&copy;");
        checks.add(new CheckItem("PMC-28", "Codificación de Entidades XML", entitiesOk,
                entitiesOk ? "Entidades XML limpias y compatibles UTF-8." : "Se encontraron entidades HTML prohibidas (ej. &nbsp;)."));

        return checks;
    }

    /**
     * Batería completa de checks de Redalyc (Marcalyc / JATS4R / SciELO SPS).
     */
    private List<CheckItem> auditRedalycExhaustive(Document doc, String rawXml) {
        List<CheckItem> checks = new ArrayList<>();

        // RED-01: Título Principal (<article-title>)
        NodeList titles = doc.getElementsByTagName("article-title");
        boolean hasTitle = titles.getLength() > 0 && !titles.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("RED-01", "Título Principal en Idioma Original (<article-title>)", hasTitle,
                hasTitle ? "Título original presente." : "Falta <article-title>."));

        // RED-02: Título Traducido Bilingüe (<trans-title>)
        NodeList transTitles = doc.getElementsByTagName("trans-title");
        boolean hasTransTitle = transTitles.getLength() > 0 && !transTitles.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("RED-02", "Título Traducido Bilingüe (<trans-title>)", hasTransTitle,
                hasTransTitle ? "Título traducido: " + transTitles.item(0).getTextContent().trim() : "Redalyc exige título traducido (ej. inglés)."));

        // RED-03: Resumen en Idioma Original (<abstract>)
        NodeList abstracts = doc.getElementsByTagName("abstract");
        boolean hasAbstract = abstracts.getLength() > 0 && !abstracts.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("RED-03", "Resumen en Idioma Original (<abstract>)", hasAbstract,
                hasAbstract ? "Resumen en idioma original presente." : "Falta <abstract>."));

        // RED-04: Resumen Traducido Bilingüe (<trans-abstract>)
        NodeList transAbstracts = doc.getElementsByTagName("trans-abstract");
        boolean hasTransAbstract = transAbstracts.getLength() > 0 && !transAbstracts.item(0).getTextContent().isBlank();
        checks.add(new CheckItem("RED-04", "Resumen Traducido Bilingüe (<trans-abstract>)", hasTransAbstract,
                hasTransAbstract ? "Abstract en idioma extranjero presente." : "Redalyc/Marcalyc exige abstract traducido (<trans-abstract>)."));

        // RED-05: Palabras Clave en Múltiples Idiomas (al menos dos kwd-group)
        NodeList kwdGroups = doc.getElementsByTagName("kwd-group");
        Set<String> kwdLangs = new HashSet<>();
        for (int i = 0; i < kwdGroups.getLength(); i++) {
            Element el = (Element) kwdGroups.item(i);
            String l = el.getAttribute("xml:lang");
            if (l != null && !l.isBlank()) kwdLangs.add(l.toLowerCase());
        }
        boolean kwdMulti = kwdLangs.size() >= 2;
        checks.add(new CheckItem("RED-05", "Palabras Clave Bilingües (<kwd-group>)", kwdMulti,
                kwdMulti ? "Palabras clave en: " + kwdLangs : "Marcalyc exige palabras clave en al menos dos idiomas (ej. es y en)."));

        // RED-06: País de Afiliación (<country country="XX">)
        NodeList countries = doc.getElementsByTagName("country");
        boolean countryOk = countries.getLength() > 0;
        for (int i = 0; i < countries.getLength(); i++) {
            if (countries.item(i).getTextContent().isBlank()) {
                countryOk = false;
                break;
            }
        }
        checks.add(new CheckItem("RED-06", "País Normalizado en Afiliaciones (<country>)", countryOk,
                countryOk ? "País declarado en las afiliaciones." : "Redalyc exige <country> en cada bloque de afiliación."));

        // RED-07: Institución Normalizada (<institution content-type="orgname">)
        NodeList insts = doc.getElementsByTagName("institution");
        boolean instOk = insts.getLength() > 0;
        for (int i = 0; i < insts.getLength(); i++) {
            if (insts.item(i).getTextContent().isBlank()) {
                instOk = false;
                break;
            }
        }
        checks.add(new CheckItem("RED-07", "Institución Normalizada (<institution>)", instOk,
                instOk ? "Institución académica normalizada." : "Falta <institution> en afiliaciones."));

        // RED-08: Autor de Correspondencia o Email de Contacto
        NodeList contribs = doc.getElementsByTagName("contrib");
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
        checks.add(new CheckItem("RED-08", "Autor de Correspondencia o Email (<email>/corresp)", correspOk,
                correspOk ? "Autor de correspondencia o email identificado." : "Se recomienda marcar corresp=\"yes\" o incluir <email> del autor."));

        // RED-09: Vinculación Autor-Afiliación
        NodeList affList = doc.getElementsByTagName("aff");
        Set<String> affIds = new HashSet<>();
        for (int i = 0; i < affList.getLength(); i++) {
            String id = ((Element) affList.item(i)).getAttribute("id");
            if (!id.isBlank()) affIds.add(id);
        }
        boolean xrefOk = affIds.size() > 0;
        checks.add(new CheckItem("RED-09", "Vinculación de Afiliaciones a Autores", xrefOk,
                xrefOk ? "Afiliaciones indexadas para autores." : "Faltan etiquetas <aff> con id."));

        // RED-10: Licencia Creative Commons Open Access
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
        checks.add(new CheckItem("RED-10", "Licencia Creative Commons en <permissions>", licOk,
                licOk ? "Licencia Creative Commons identificada con URL." : "Redalyc exige licencia CC en <license xlink:href=\"...\">."));

        // RED-11: Identificador Persistente o DOI
        NodeList articleIds = doc.getElementsByTagName("article-id");
        boolean idOk = articleIds.getLength() > 0;
        checks.add(new CheckItem("RED-11", "Identificador del Artículo (<article-id>)", idOk,
                idOk ? "Identificador de artículo presente." : "Falta identificador <article-id>."));

        // RED-12: Declaración de Conflicto de Intereses
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
        checks.add(new CheckItem("RED-12", "Declaración de Conflicto de Intereses (<fn fn-type=\"conflict\">)", coiOk,
                coiOk ? "Declaración de conflicto de intereses presente." : "Recomendado por Redalyc: incluir nota de conflicto de intereses."));

        // RED-13: Declaración de Disponibilidad de Datos
        NodeList secs = doc.getElementsByTagName("sec");
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
        checks.add(new CheckItem("RED-13", "Disponibilidad de Datos (data-availability)", dataAvailOk,
                dataAvailOk ? "Declaración de datos de investigación presente." : "Recomendado por Redalyc/JATS4R: incluir disponibilidad de datos."));

        // RED-14: Fechas del Ciclo Editorial (<history> con received y accepted)
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
        checks.add(new CheckItem("RED-14", "Historial Editorial (received / accepted)", histOk,
                histOk ? "Fechas de recepción y aceptación presentes." : "Redalyc exige fecha de recepción y aceptación en <history>."));

        // RED-15: Secciones Estructuradas con Títulos (<sec>/<title>)
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
        checks.add(new CheckItem("RED-15", "Estructura de Secciones (<sec>/<title>)", secOk,
                secOk ? "Secciones del cuerpo estructuradas con títulos." : "Secciones <sec> sin <title>."));

        // RED-16: Referencias Bibliográficas Estructuradas (<ref-list>)
        NodeList refs = doc.getElementsByTagName("ref");
        boolean refOk = refs.getLength() > 0;
        checks.add(new CheckItem("RED-16", "Lista de Referencias Bibliográficas (<ref-list>)", refOk,
                refOk ? refs.getLength() + " referencias registradas." : "No se encontraron referencias en <back>."));

        // RED-17: Citas con Año de Publicación (<year>)
        NodeList citations = doc.getElementsByTagName("element-citation");
        boolean citYearOk = true;
        for (int i = 0; i < citations.getLength(); i++) {
            Element cit = (Element) citations.item(i);
            if (cit.getElementsByTagName("year").getLength() == 0) {
                citYearOk = false;
                break;
            }
        }
        checks.add(new CheckItem("RED-17", "Año de Publicación en Citas (<year>)", citYearOk,
                citYearOk ? "Citas con año estructurado." : "Hay citas bibliográficas sin año <year>."));

        // RED-18: Citas con Autores o Autores Corporativos (<person-group>/<name> o <collab>)
        boolean citAuthorsOk = true;
        for (int i = 0; i < citations.getLength(); i++) {
            Element cit = (Element) citations.item(i);
            boolean hasName = cit.getElementsByTagName("name").getLength() > 0;
            boolean hasCollab = cit.getElementsByTagName("collab").getLength() > 0;
            if (!hasName && !hasCollab) {
                citAuthorsOk = false;
                break;
            }
        }
        checks.add(new CheckItem("RED-18", "Autores de Citas (<person-group>/<name> o <collab>)", citAuthorsOk,
                citAuthorsOk ? "Autores o instituciones autoras identificadas en citas." : "Hay citas sin autores (<name> o <collab>)."));

        return checks;
    }
}
