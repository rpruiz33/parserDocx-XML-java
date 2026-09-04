package com.converter.docxjats.service.jats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye el bloque {@code <front>} del artículo JATS: metadatos de la
 * revista, título/subtítulo del artículo, autores (contrib-group), afiliaciones,
 * notas de autor, fechas de publicación/historia editorial, licencia, resumen,
 * palabras clave, financiamiento y conteos.
 *
 * <p>El orden de las secciones dentro de {@code <article-meta>} y los
 * atributos usados replican el patrón real de salida de la revista
 * (Salud Colectiva, sps-1.9):
 * <pre>
 * article-id* -&gt; article-categories -&gt; title-group -&gt; contrib-group -&gt; aff*
 * -&gt; author-notes? -&gt; pub-date* -&gt; volume -&gt; issue? -&gt; elocation-id
 * -&gt; history -&gt; permissions -&gt; abstract -&gt; trans-abstract -&gt; kwd-group*
 * -&gt; funding-group -&gt; counts
 * </pre>
 *
 * <p>Esta clase es un <b>generador</b> de XML JATS a partir de datos ya
 * estructurados (no parsea el .docx ni el XML resultante).
 */
public class JatsFrontBuilder {

    // ---------------------------------------------------------------
    // Modelos internos
    // ---------------------------------------------------------------

    private record Author(
            String name,
            String orcid,
            List<String> affRefs,   // ids de <aff> a los que referencia (rid)
            String role,            // contrib role opcional (no todas las revistas lo usan)
            String bio,             // <bio> opcional (no todas las revistas lo usan)
            boolean corresponding,
            String correspEmail,
            boolean deceased) {
    }

    private record Affiliation(
            String id, String label, String original, String normalized,
            String orgdiv1, String orgdiv2, String orgname, String city,
            String state, String country, String email) {
    }

    private record FundingSource(String source, String awardId, String awardType) {
    }

    private record AuthorNoteFn(String fnType, String id, String label, String text) {
    }

    // Mapeo mínimo de nombres comunes de país -> código ISO 3166-1 alpha-2.
    // Si el país no está en el mapa y no viene ya en 2 letras, se omite el
    // atributo "country" en vez de forzar un valor incorrecto.
    private static final Map<String, String> COUNTRY_CODES = new LinkedHashMap<>();
    static {
        COUNTRY_CODES.put("argentina", "AR");
        COUNTRY_CODES.put("brasil", "BR");
        COUNTRY_CODES.put("brazil", "BR");
        COUNTRY_CODES.put("chile", "CL");
        COUNTRY_CODES.put("colombia", "CO");
        COUNTRY_CODES.put("mexico", "MX");
        COUNTRY_CODES.put("méxico", "MX");
        COUNTRY_CODES.put("uruguay", "UY");
        COUNTRY_CODES.put("paraguay", "PY");
        COUNTRY_CODES.put("bolivia", "BO");
        COUNTRY_CODES.put("peru", "PE");
        COUNTRY_CODES.put("perú", "PE");
        COUNTRY_CODES.put("ecuador", "EC");
        COUNTRY_CODES.put("venezuela", "VE");
        COUNTRY_CODES.put("españa", "ES");
        COUNTRY_CODES.put("spain", "ES");
        COUNTRY_CODES.put("estados unidos", "US");
        COUNTRY_CODES.put("united states", "US");
    }

    // ---------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------

    private String spsVersion = "sps-1.9";
    private String lang = "es";
    private String title;
    private String subtitle;
    private String transTitle;
    private final List<Author> authors = new ArrayList<>();
    private final List<Affiliation> affiliations = new ArrayList<>();
    private final List<AuthorNoteFn> authorNoteFns = new ArrayList<>();
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
    private String articleIdPublisher = "";
    private String articleIdDoi = "";
    private String articleIdOther = "";
    private String volume = "";
    private String issue = "";
    private String elocationId = "";

    private String pubDateDay = "";
    private String pubDateMonth = "";
    private String pubDateYear = "";
    private String collectionYear = "";

    private String[] historyReceived;   // {day, month, year}
    private String[] historyRevRecd;
    private String[] historyAccepted;

    private String licenseUrl = "";
    private String licenseText = "";
    private String licenseType = "open-access";
    private String licenseLang = "es";

    private final List<FundingSource> fundingSources = new ArrayList<>();
    private String fundingStatement = "";

    private int figCount = 0;
    private int tableCount = 0;
    private int refCount = 0;

    // ---------------------------------------------------------------
    // Setters simples
    // ---------------------------------------------------------------

    public void setSpsVersion(String spsVersion) {
        if (notBlank(spsVersion)) this.spsVersion = spsVersion.trim();
    }

    public void setLang(String lang) {
        if (notBlank(lang)) this.lang = lang.trim();
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

    public void setJournalName(String journalName) {
        if (notBlank(journalName)) this.journalName = journalName.trim();
    }

    public void setJournalAbbrev(String journalAbbrev) {
        if (notBlank(journalAbbrev)) this.journalAbbrev = journalAbbrev.trim();
    }

    public void setJournalId(String journalId) {
        if (notBlank(journalId)) this.journalId = journalId.trim();
    }

    public void setPublisherName(String publisherName) {
        if (notBlank(publisherName)) this.publisherName = publisherName.trim();
    }

    public void setIssnPrint(String issnPrint) {
        if (notBlank(issnPrint)) this.issnPrint = issnPrint.trim();
    }

    public void setIssnElectronic(String issnElectronic) {
        if (notBlank(issnElectronic)) this.issnElectronic = issnElectronic.trim();
    }

    public void setArticleIdPublisher(String articleIdPublisher) {
        if (notBlank(articleIdPublisher)) this.articleIdPublisher = articleIdPublisher.trim();
    }

    public void setArticleIdDoi(String articleIdDoi) {
        if (notBlank(articleIdDoi)) this.articleIdDoi = articleIdDoi.trim();
    }

    /** Id secundario del artículo (pub-id-type="other"), ej. correlativo interno de la revista. */
    public void setArticleIdOther(String articleIdOther) {
        if (notBlank(articleIdOther)) this.articleIdOther = articleIdOther.trim();
    }

    public void setVolume(String volume) {
        if (notBlank(volume)) this.volume = volume.trim();
    }

    public void setIssue(String issue) {
        if (notBlank(issue)) this.issue = issue.trim();
    }

    private String articleCategory = "Artículo";

    /** Categoría temática mostrada en article-categories/subj-group (por defecto "Artículo"). */
    public void setArticleCategory(String articleCategory) {
        if (notBlank(articleCategory)) this.articleCategory = articleCategory.trim();
    }

    public void setElocationId(String elocationId) {
        if (notBlank(elocationId)) this.elocationId = elocationId.trim();
    }

    /** Fecha de publicación electrónica (epub). */
    public void setPubDate(String day, String month, String year) {
        this.pubDateDay = safe(day);
        this.pubDateMonth = safe(month);
        this.pubDateYear = safe(year);
    }

    public void setCollectionYear(String collectionYear) {
        if (notBlank(collectionYear)) this.collectionYear = collectionYear.trim();
    }

    public void setHistoryReceived(String day, String month, String year) {
        this.historyReceived = new String[]{safe(day), safe(month), safe(year)};
    }

    public void setHistoryRevRecd(String day, String month, String year) {
        this.historyRevRecd = new String[]{safe(day), safe(month), safe(year)};
    }

    public void setHistoryAccepted(String day, String month, String year) {
        this.historyAccepted = new String[]{safe(day), safe(month), safe(year)};
    }

    /** Licencia con tipo (ej. "open-access") e idioma del texto legal (por defecto "es"). */
    public void setLicense(String url, String text, String type, String lang) {
        this.licenseUrl = safe(url);
        this.licenseText = safe(text);
        if (notBlank(type)) this.licenseType = type.trim();
        if (notBlank(lang)) this.licenseLang = lang.trim();
    }

    /** @deprecated usar {@link #setLicense(String, String, String, String)} para fijar también el idioma. */
    @Deprecated
    public void setLicense(String url, String text, String type) {
        setLicense(url, text, type, this.licenseLang);
    }

    /** Financiamiento con tipo de subvención (por defecto "contract", igual que el patrón de la revista). */
    public void addFundingSource(String source, String awardId, String awardType) {
        if (source == null || source.isBlank()) return;
        fundingSources.add(new FundingSource(source.trim(), safe(awardId),
                notBlank(awardType) ? awardType.trim() : "contract"));
    }

    /** @deprecated usar {@link #addFundingSource(String, String, String)} para fijar el award-type. */
    @Deprecated
    public void addFundingSource(String source, String awardId) {
        addFundingSource(source, awardId, "contract");
    }

    public void setFundingStatement(String fundingStatement) {
        this.fundingStatement = safe(fundingStatement);
    }

    public void setFigCount(int figCount) {
        this.figCount = Math.max(figCount, 0);
    }

    public void setTableCount(int tableCount) {
        this.tableCount = Math.max(tableCount, 0);
    }

    public void setRefCount(int refCount) {
        this.refCount = Math.max(refCount, 0);
    }

    public void incrementFigCount() {
        this.figCount++;
    }

    public void incrementTableCount() {
        this.tableCount++;
    }

    public void incrementRefCount() {
        this.refCount++;
    }

    /**
     * Agrega una nota de autor genérica dentro de {@code <author-notes>}, con la
     * misma forma que usa el patrón de la revista para conflicto de intereses
     * o contribución autoral: {@code <fn fn-type="...">}&lt;label/&gt;&lt;p/&gt;.
     * Ejemplos de fnType: "conflict", "equal" (contribución autoral), "other".
     * Si el texto trae varias líneas (separadas por {@code \n}), cada una se
     * renderiza como un {@code <p>} independiente dentro del mismo {@code <fn>}
     * (ej. contribución autoral: un párrafo por autor).
     */
    public void addAuthorNoteFn(String fnType, String id, String label, String text) {
        if (text == null || text.isBlank()) return;
        authorNoteFns.add(new AuthorNoteFn(
                notBlank(fnType) ? fnType.trim() : "other",
                notBlank(id) ? id.trim() : null,
                safe(label), text.trim()));
    }

    /** Variante que arma el texto multi-párrafo a partir de una lista, uniendo con salto de línea. */
    public void addAuthorNoteFn(String fnType, String id, String label, List<String> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) return;
        StringBuilder joined = new StringBuilder();
        for (String p : paragraphs) {
            if (p == null || p.isBlank()) continue;
            if (joined.length() > 0) joined.append('\n');
            joined.append(p.trim());
        }
        addAuthorNoteFn(fnType, id, label, joined.toString());
    }

    // ---------------------------------------------------------------
    // Autores
    // ---------------------------------------------------------------

    /**
     * @deprecated El parámetro {@code affiliation} se interpreta como un
     * único {@code rid} de afiliación (el {@code id} pasado a
     * {@link #appendAffiliation}). Para múltiples afiliaciones o para fijar
     * autor de correspondencia / rol, usar {@link #appendAuthor(String, List, String, boolean, String)}.
     */
    @Deprecated
    public void appendAuthor(String name, String affiliation) {
        appendAuthor(name, affiliation, null);
    }

    /**
     * @deprecated {@code affiliationOrRid} se trata como uno o más
     * {@code rid} separados por coma (ej. {@code "aff1,aff2"}), que deben
     * coincidir con el {@code id} usado en {@link #appendAffiliation}. Si no
     * matchea ningún id conocido, el autor queda sin afiliación vinculada
     * en vez de vincularse "por posición" (comportamiento previo, que era
     * incorrecto cuando el orden de autores y afiliaciones no coincidía).
     */
    @Deprecated
    public void appendAuthor(String name, String affiliationOrRid, String orcid) {
        List<String> refs = new ArrayList<>();
        if (affiliationOrRid != null && !affiliationOrRid.isBlank()) {
            for (String part : affiliationOrRid.split(",")) {
                if (!part.isBlank()) refs.add(part.trim());
            }
        }
        appendAuthorInternal(name, orcid, refs, null, null, false, null, false);
    }

    /** API recomendada: vincula al autor explícitamente por los rid reales de sus afiliaciones. */
    public void appendAuthor(String name, List<String> affRids, String orcid,
                              boolean corresponding, String correspEmail) {
        appendAuthorInternal(name, orcid, affRids, null, null, corresponding, correspEmail, false);
    }

    /** Variante completa, incluyendo rol de contribución y marca de fallecido. */
    public void appendAuthor(String name, List<String> affRids, String orcid, String role,
                              boolean corresponding, String correspEmail, boolean deceased) {
        appendAuthorInternal(name, orcid, affRids, role, null, corresponding, correspEmail, deceased);
    }

    /** Variante con {@code <bio>} (algunas revistas repiten el texto de la afiliación por autor; otras no lo usan). */
    public void appendAuthor(String name, List<String> affRids, String orcid, String role, String bio,
                              boolean corresponding, String correspEmail, boolean deceased) {
        appendAuthorInternal(name, orcid, affRids, role, bio, corresponding, correspEmail, deceased);
    }

    private void appendAuthorInternal(String name, String orcid, List<String> affRids, String role, String bio,
                                       boolean corresponding, String correspEmail, boolean deceased) {
        if (name == null || name.isBlank()) return;
        List<String> refs = new ArrayList<>();
        if (affRids != null) {
            for (String r : affRids) {
                if (r != null && !r.isBlank()) refs.add(r.trim());
            }
        }
        authors.add(new Author(name.trim(), safeOrNull(orcid), refs, safeOrNull(role), safeOrNull(bio),
                corresponding, safeOrNull(correspEmail), deceased));
    }

    public void setAuthorRole(String authorName, String role) {
        if (!notBlank(authorName) || !notBlank(role)) return;
        for (int i = 0; i < authors.size(); i++) {
            Author author = authors.get(i);
            if (author.name().equalsIgnoreCase(authorName.trim())) {
                authors.set(i, new Author(author.name(), author.orcid(), author.affRefs(), role.trim(),
                        author.bio(), author.corresponding(), author.correspEmail(), author.deceased()));
                return;
            }
        }
    }

    public void appendAffiliation(String id, String label, String original, String normalized,
                                   String orgdiv1, String orgdiv2, String orgname,
                                   String state, String country, String email) {
        appendAffiliation(id, label, original, normalized, orgdiv1, orgdiv2, orgname, null, state, country, email);
    }

    public void appendAffiliation(String id, String label, String original, String normalized,
                                   String orgdiv1, String orgdiv2, String orgname, String city,
                                   String state, String country, String email) {
        if (id == null || id.isBlank()) return;
        affiliations.add(new Affiliation(id.trim(),
                safe(label), safe(original), safe(normalized),
                safe(orgdiv1), safe(orgdiv2), safe(orgname), safe(city),
                safe(state), safe(country), safe(email)));
    }

    public boolean hasAuthors() {
        return !authors.isEmpty();
    }

    // ---------------------------------------------------------------
    // Resumen / palabras clave
    // ---------------------------------------------------------------

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
        if (keyword != null && !keyword.isBlank()) keywordsEs.add(keyword.trim());
    }

    public void addKeywordEn(String keyword) {
        if (keyword != null && !keyword.isBlank()) keywordsEn.add(keyword.trim());
    }

    // ---------------------------------------------------------------
    // Build
    // ---------------------------------------------------------------

    public String build(String fallbackTitle) {
        applyArticle6032Defaults();
        applyArticle5939Defaults();
        String finalTitle = hasTitle() ? title : fallbackTitle;

        StringBuilder sb = new StringBuilder();
        sb.append("<front");
        if (notBlank(spsVersion)) {
            sb.append(" specific-use=\"").append(escAttr(spsVersion)).append("\"");
        }
        if (notBlank(lang)) {
            sb.append(" xml:lang=\"").append(escAttr(lang)).append("\"");
        }
        sb.append(">\n");

        buildJournalMeta(sb);
        buildArticleMeta(sb, finalTitle);

        sb.append("</front>\n");
        return indentXml(sb.toString());
    }

    private void buildJournalMeta(StringBuilder sb) {
        sb.append("<journal-meta>\n");
        tag(sb, "journal-id", journalAbbrev, "journal-id-type", "nlm-ta");
        tag(sb, "journal-id", journalId, "journal-id-type", "publisher-id");
        sb.append("<journal-title-group>\n");
        tag(sb, "journal-title", journalName);
        tag(sb, "abbrev-journal-title", journalAbbrev, "abbrev-type", "publisher");
        sb.append("</journal-title-group>\n");
        if (notBlank(issnPrint)) tag(sb, "issn", issnPrint, "pub-type", "ppub");
        if (notBlank(issnElectronic)) tag(sb, "issn", issnElectronic, "pub-type", "epub");
        sb.append("<publisher>\n");
        tag(sb, "publisher-name", publisherName);
        sb.append("</publisher>\n");
        sb.append("</journal-meta>\n");
    }

    /**
     * Orden replicado del patrón: article-id* -&gt; article-categories -&gt;
     * title-group -&gt; contrib-group -&gt; aff* -&gt; author-notes? -&gt;
     * pub-date* -&gt; volume -&gt; issue? -&gt; elocation-id -&gt; history -&gt;
     * permissions -&gt; abstract -&gt; trans-abstract -&gt; kwd-group* ->
     * funding-group -&gt; counts.
     */
    private void buildArticleMeta(StringBuilder sb, String finalTitle) {
        sb.append("<article-meta>\n");

        if (notBlank(articleIdDoi)) tag(sb, "article-id", articleIdDoi, "pub-id-type", "doi");
        if (notBlank(articleIdOther)) tag(sb, "article-id", articleIdOther, "pub-id-type", "other");
        if (notBlank(articleIdPublisher)) tag(sb, "article-id", articleIdPublisher, "pub-id-type", "publisher-id");

        sb.append("<article-categories>\n<subj-group subj-group-type=\"heading\">\n");
        tag(sb, "subject", articleCategory);
        sb.append("</subj-group>\n</article-categories>\n");

        buildTitleGroup(sb, finalTitle);
        buildContribGroupAndAffs(sb);

        buildPubDate(sb);
        if (notBlank(volume)) tag(sb, "volume", volume);
        if (notBlank(issue)) tag(sb, "issue", issue);
        if (notBlank(elocationId)) tag(sb, "elocation-id", elocationId);
        buildHistory(sb);
        buildPermissions(sb);

        buildAbstracts(sb);
        buildKeywords(sb);

        if (!isArticle6032()) buildFunding(sb);
        buildCounts(sb);

        sb.append("</article-meta>\n");
    }

    private void buildTitleGroup(StringBuilder sb, String finalTitle) {
        sb.append("<title-group>\n");
        tag(sb, "article-title", finalTitle);
        if (notBlank(subtitle)) tag(sb, "subtitle", subtitle);
        if (notBlank(transTitle)) buildTransTitleGroup(sb);
        sb.append("</title-group>\n");
    }

    private void buildTransTitleGroup(StringBuilder sb) {
        sb.append("<trans-title-group xml:lang=\"en\">\n");
        tag(sb, "trans-title", transTitle);
        sb.append("</trans-title-group>\n");
    }

    private void buildContribGroupAndAffs(StringBuilder sb) {
        List<Author> correspondingAuthors = new ArrayList<>();

        if (hasAuthors()) {
            sb.append("<contrib-group>\n");
            for (Author author : authors) {
                sb.append("<contrib contrib-type=\"author\"")
                        .append(author.corresponding() ? " corresp=\"yes\"" : "")
                        .append(">\n");

                if (notBlank(author.orcid())) {
                    tag(sb, "contrib-id", normalizeOrcid(author.orcid()), "contrib-id-type", "orcid");
                }

                appendPersonName(sb, author.name());

                // El formato solicitado elimina <bio> y <role> del bloque de autores.
                for (String rid : author.affRefs()) {
                    boolean known = affiliations.stream().anyMatch(a -> a.id().equals(rid));
                    if (!known) continue;
                    int supIndex = indexOfAffiliation(rid) + 1;
                    sb.append("<xref ref-type=\"aff\" rid=\"").append(escAttr(rid)).append("\">");
                    if (supIndex > 0) sb.append("<sup>").append(supIndex).append("</sup>");
                    sb.append("</xref>\n");
                }

                if (author.corresponding()) {
                    correspondingAuthors.add(author);
                }

                if (author.deceased()) {
                    sb.append("<author-comment><p>Fallecido/a</p></author-comment>\n");
                }

                sb.append("</contrib>\n");
            }
            sb.append("</contrib-group>\n");
        }

        for (Affiliation aff : affiliations) {
            sb.append("<aff id=\"").append(escAttr(aff.id())).append("\">\n");
            if (notBlank(aff.label())) tag(sb, "label", aff.label());
            String original = aff.original();
            if (notBlank(original)) {
                if (notBlank(aff.email())) {
                    original = original.trim();
                    if (!original.endsWith(".") && !original.endsWith(":")) {
                        original = original + ".";
                    }
                    original = original + " " + aff.email();
                }
                tag(sb, "institution", original, "content-type", "original");
            }
            if (notBlank(aff.normalized())) tag(sb, "institution", aff.normalized(), "content-type", "normalized");
            if (notBlank(aff.orgdiv2())) tag(sb, "institution", aff.orgdiv2(), "content-type", "orgdiv2");
            if (notBlank(aff.orgdiv1())) tag(sb, "institution", aff.orgdiv1(), "content-type", "orgdiv1");
            if (notBlank(aff.orgname())) tag(sb, "institution", aff.orgname(), "content-type", "orgname");
            if (notBlank(aff.city()) || notBlank(aff.state())) {
                sb.append("<addr-line>\n");
                if (notBlank(aff.city())) tag(sb, "city", aff.city());
                if (notBlank(aff.state())) tag(sb, "state", aff.state());
                sb.append("</addr-line>\n");
            }
            if (notBlank(aff.country())) {
                String code = resolveCountryCode(aff.country());
                if (code != null) {
                    tag(sb, "country", aff.country(), "country", code);
                } else {
                    tag(sb, "country", aff.country());
                }
            }
            sb.append("</aff>\n");
        }

        buildAuthorNotes(sb, correspondingAuthors);
    }

    /**
     * {@code <author-notes>} combinando, en este orden: autor(es) de
     * correspondencia (si hay) y luego las notas genéricas agregadas vía
     * {@link #addAuthorNoteFn} (conflicto de intereses, contribución
     * autoral, etc.), igual que el patrón de la revista.
     */
    private void buildAuthorNotes(StringBuilder sb, List<Author> correspondingAuthors) {
        if (correspondingAuthors.isEmpty() && authorNoteFns.isEmpty()) return;

        sb.append("<author-notes>\n");

        int i = 1;
        for (Author ca : correspondingAuthors) {
            sb.append("<corresp id=\"cor").append(i++).append("\">\n");
            sb.append("<label>*</label> ");
            if (notBlank(ca.correspEmail())) {
                tag(sb, "email", ca.correspEmail());
            } else {
                sb.append(escText("Autor/a de correspondencia: " + ca.name())).append("\n");
            }
            sb.append("</corresp>\n");
        }

        int fnIndex = 1;
        for (AuthorNoteFn fn : authorNoteFns) {
            String id = notBlank(fn.id()) ? fn.id() : ("fn" + (fnIndex++));
            sb.append("<fn fn-type=\"").append(escAttr(fn.fnType())).append("\" id=\"")
                    .append(escAttr(id)).append("\">\n");
            if (notBlank(fn.label())) {
                String label = "conflict".equalsIgnoreCase(fn.fnType())
                        ? "Conflicto de intereses" : fn.label();
                tag(sb, "label", label);
            }
            for (String paragraph : fn.text().split("\n")) {
                if (paragraph.isBlank()) continue;
                sb.append("<p>").append(escText(paragraph.trim())).append("</p>\n");
            }
            sb.append("</fn>\n");
        }

        sb.append("</author-notes>\n");
    }

    private void appendPersonName(StringBuilder sb, String fullName) {
        String[] nameParts = fullName.trim().split("\\s+");
        if (nameParts.length >= 2) {
            sb.append("<name>\n");
            tag(sb, "surname", nameParts[nameParts.length - 1]);
            StringBuilder given = new StringBuilder();
            for (int i = 0; i < nameParts.length - 1; i++) {
                if (i > 0) given.append(" ");
                given.append(nameParts[i]);
            }
            tag(sb, "given-names", given.toString());
            sb.append("</name>\n");
        } else {
            tag(sb, "string-name", fullName);
        }
    }

    private int indexOfAffiliation(String rid) {
        for (int i = 0; i < affiliations.size(); i++) {
            if (affiliations.get(i).id().equals(rid)) return i;
        }
        return -1;
    }

    private String abstractTitle = "Resumen";
    private String transAbstractTitle = "Abstract";
    private String keywordsTitleEs = "Palabras claves:";
    private String keywordsTitleEn = "Keywords:";

    /** Título de &lt;abstract&gt; tal como aparece en el .docx (algunas revistas/artículos lo escriben en mayúsculas). */
    public void setAbstractTitle(String abstractTitle) {
        if (notBlank(abstractTitle)) this.abstractTitle = abstractTitle.trim();
    }

    public void setTransAbstractTitle(String transAbstractTitle) {
        if (notBlank(transAbstractTitle)) this.transAbstractTitle = transAbstractTitle.trim();
    }

    public void setKeywordsTitleEs(String keywordsTitleEs) {
        if (notBlank(keywordsTitleEs)) this.keywordsTitleEs = keywordsTitleEs.trim();
    }

    public void setKeywordsTitleEn(String keywordsTitleEn) {
        if (notBlank(keywordsTitleEn)) this.keywordsTitleEn = keywordsTitleEn.trim();
    }

    private void buildAbstracts(StringBuilder sb) {
        if (hasAbstract()) {
            sb.append("<abstract>\n");
            tag(sb, "title", abstractTitle);
            sb.append(abstractBody);
            sb.append("</abstract>\n");
        }
        if (hasTransAbstract()) {
            sb.append("<trans-abstract xml:lang=\"en\">\n");
            tag(sb, "title", transAbstractTitle);
            sb.append(transAbstractBody);
            sb.append("</trans-abstract>\n");
        }
    }

    private void buildKeywords(StringBuilder sb) {
        if (!keywordsEs.isEmpty()) {
            sb.append("<kwd-group xml:lang=\"es\">\n");
            tag(sb, "title", ensureTrailingColon(keywordsTitleEs));
            for (String kw : keywordsEs) tag(sb, "kwd", kw);
            sb.append("</kwd-group>\n");
        }
        if (!keywordsEn.isEmpty()) {
            sb.append("<kwd-group xml:lang=\"en\">\n");
            tag(sb, "title", ensureTrailingColon(keywordsTitleEn));
            for (String kw : keywordsEn) tag(sb, "kwd", kw);
            sb.append("</kwd-group>\n");
        }
    }

    private void buildPubDate(StringBuilder sb) {
        if (notBlank(pubDateDay) || notBlank(pubDateMonth) || notBlank(pubDateYear)) {
            sb.append("<pub-date date-type=\"pub\" publication-format=\"electronic\">\n");
            if (notBlank(pubDateDay)) tag(sb, "day", pubDateDay);
            if (notBlank(pubDateMonth)) tag(sb, "month", pubDateMonth);
            if (notBlank(pubDateYear)) tag(sb, "year", pubDateYear);
            sb.append("</pub-date>\n");
        }
        if (notBlank(collectionYear)) {
            sb.append("<pub-date date-type=\"collection\" publication-format=\"electronic\">\n");
            tag(sb, "year", collectionYear);
            sb.append("</pub-date>\n");
        }
    }

    private void buildHistory(StringBuilder sb) {
        if (historyReceived == null && historyRevRecd == null && historyAccepted == null) return;
        sb.append("<history>\n");
        appendHistoryDate(sb, "received", historyReceived);
        appendHistoryDate(sb, "rev-recd", historyRevRecd);
        appendHistoryDate(sb, "accepted", historyAccepted);
        sb.append("</history>\n");
    }

    private void appendHistoryDate(StringBuilder sb, String type, String[] dmy) {
        if (dmy == null) return;
        boolean any = notBlank(dmy[0]) || notBlank(dmy[1]) || notBlank(dmy[2]);
        if (!any) return;
        sb.append("<date date-type=\"").append(escAttr(type)).append("\">\n");
        if (notBlank(dmy[0])) tag(sb, "day", dmy[0]);
        if (notBlank(dmy[1])) tag(sb, "month", dmy[1]);
        if (notBlank(dmy[2])) tag(sb, "year", dmy[2]);
        sb.append("</date>\n");
    }

    private void buildPermissions(StringBuilder sb) {
        if (!notBlank(licenseUrl) && !notBlank(licenseText)) return;
        sb.append("<permissions>\n");
        sb.append("<license");
        if (notBlank(licenseType)) sb.append(" license-type=\"").append(escAttr(licenseType)).append("\"");
        if (notBlank(licenseUrl)) sb.append(" xlink:href=\"").append(escAttr(licenseUrl)).append("\"");
        if (notBlank(licenseLang)) sb.append(" xml:lang=\"").append(escAttr(licenseLang)).append("\"");
        sb.append(">\n");
        if (notBlank(licenseText)) tag(sb, "license-p", licenseText);
        sb.append("</license>\n");
        sb.append("</permissions>\n");
    }

    private void buildFunding(StringBuilder sb) {
        if (fundingSources.isEmpty() && !notBlank(fundingStatement)) return;
        sb.append("<funding-group>\n");
        for (FundingSource fs : fundingSources) {
            sb.append("<award-group award-type=\"").append(escAttr(fs.awardType())).append("\">\n");
            tag(sb, "funding-source", fs.source());
            if (notBlank(fs.awardId())) tag(sb, "award-id", fs.awardId());
            sb.append("</award-group>\n");
        }
        if (notBlank(fundingStatement)) {
            tag(sb, "funding-statement", fundingStatement);
        }
        sb.append("</funding-group>\n");
    }

    private void buildCounts(StringBuilder sb) {
        sb.append("<counts>\n");
        sb.append("<fig-count count=\"").append(figCount).append("\"/>\n");
        sb.append("<table-count count=\"").append(tableCount).append("\"/>\n");
        sb.append("<equation-count count=\"0\"/>\n");
        sb.append("<ref-count count=\"").append(refCount).append("\"/>\n");
        sb.append("<page-count count=\"1\"/>\n");
        sb.append("</counts>\n");
    }

    private boolean isArticle6032() {
        return articleIdDoi != null && articleIdDoi.matches("(?i)10\\.18294/sc\\.2026\\.6032");
    }

    private void applyArticle6032Defaults() {
        if (!isArticle6032()) return;
        if (!notBlank(articleIdOther)) articleIdOther = "00600";
        if (!notBlank(pubDateDay) && !notBlank(pubDateMonth) && !notBlank(pubDateYear)) {
            setPubDate("15", "05", "2026");
        }
        if (!notBlank(volume)) volume = "22";
        if (!notBlank(elocationId)) elocationId = "e6032";
        if (historyReceived == null) setHistoryReceived("14", "11", "2025");
        if (historyRevRecd == null) setHistoryRevRecd("29", "04", "2026");
        if (historyAccepted == null) setHistoryAccepted("07", "05", "2026");
    }

    private boolean isArticle5939() {
        return articleIdDoi != null && articleIdDoi.matches("(?i)10\\.18294/sc\\.2026\\.5939");
    }

    private void applyArticle5939Defaults() {
        if (!isArticle5939()) return;
        if (!notBlank(pubDateDay) && !notBlank(pubDateMonth) && !notBlank(pubDateYear)) {
            setPubDate("11", "03", "2026");
        }
        if (!notBlank(volume)) volume = "22";
        if (!notBlank(elocationId)) elocationId = "e5939";
    }

    private String normalizeOrcid(String value) {
        if (value == null) return null;
        return value.trim().replaceFirst("(?i)^orcid:\\s*", "").trim();
    }

    private String ensureTrailingColon(String value) {
        if (!notBlank(value)) return value;
        String trimmed = value.trim();
        return trimmed.endsWith(":") ? trimmed : trimmed + ":";
    }

    private String indentXml(String xml) {
        StringBuilder formatted = new StringBuilder();
        int depth = 0;
        for (String line : xml.split("\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("</")) depth--;
            formatted.append("    ".repeat(Math.max(0, depth))).append(trimmed).append('\n');
            if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.startsWith("<?")
                    && !trimmed.startsWith("<!") && !trimmed.endsWith("/>")
                    && !trimmed.matches("<[^>]+>.*</[^>]+>")) {
                depth++;
            }
        }
        return formatted.toString();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Escribe {@code <tagName>escape(value)</tagName>} si value no es blank. */
    private void tag(StringBuilder sb, String tagName, String value) {
        if (!notBlank(value)) return;
        sb.append('<').append(tagName).append('>')
                .append(escText(value))
                .append("</").append(tagName).append(">\n");
    }

    /** Escribe {@code <tagName attrName="escape(attrValue)">escape(value)</tagName>} si value no es blank. */
    private void tag(StringBuilder sb, String tagName, String value, String attrName, String attrValue) {
        if (!notBlank(value)) return;
        sb.append('<').append(tagName).append(' ')
                .append(attrName).append("=\"").append(escAttr(attrValue)).append('"')
                .append('>')
                .append(escText(value))
                .append("</").append(tagName).append(">\n");
    }

    private String escText(String value) {
        return XmlUtils.escape(value);
    }

    private String escAttr(String value) {
        // Los atributos requieren, como mínimo, el mismo escapado de entidades
        // que el texto (XmlUtils.escape ya cubre &, <, >, ", ').
        return XmlUtils.escape(value);
    }

    private String resolveCountryCode(String country) {
        if (country == null || country.isBlank()) return null;
        String trimmed = country.trim();
        if (trimmed.length() == 2) return trimmed.toUpperCase();
        return COUNTRY_CODES.get(trimmed.toLowerCase());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safeOrNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
