package com.converter.docxjats.service.jats;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Lee el {@code .docx} fuente que usa esta revista (metadatos fijos por
 * posición al inicio del documento, seguidos de título, autores,
 * afiliaciones, resumen/abstract, palabras clave, cuerpo, financiamiento,
 * conflicto de intereses, contribución autoral, referencias e historia
 * editorial) y llena un {@link JatsFrontBuilder}.
 *
 * <h2>Diseño</h2>
 * <p>Se recorre la lista de párrafos del documento con una <b>máquina de
 * estados</b> ({@link State}): cada estado sabe qué forma tiene el párrafo
 * que le corresponde (negrita, superíndice, hipervínculo, numeración de
 * lista) y cuándo debe ceder el turno al siguiente estado. Dentro de cada
 * estado se usan <b>expresiones regulares</b> para separar la etiqueta del
 * contenido (ej. {@code "Resumen: ..."}), para detectar el DOI, emails,
 * fechas de historia editorial, y para inferir subcampos de afiliación
 * (departamento, programa, institución, estado, país) a partir de texto
 * libre.
 *
 * <h2>Confiabilidad por sección</h2>
 * <ul>
 *   <li><b>Alta</b> (mecánica, no depende de heurísticas de lenguaje natural):
 *       metadatos de revista, DOI, título/trans-título, autores + ORCID,
 *       vínculo autor-afiliación, resumen/abstract, palabras clave,
 *       conflicto de intereses, contribución autoral, historia editorial,
 *       conteo de referencias.</li>
 *   <li><b>Heurística</b> (funciona en este documento y en documentos con
 *       la misma convención, pero conviene que un editor la revise):
 *       separación de la afiliación en departamento/programa/institución/
 *       estado/país, y separación del financiamiento en fuente + código de
 *       subvención. El texto libre de estas secciones varía demasiado entre
 *       artículos como para garantizar 100% de acierto solo con regex.</li>
 * </ul>
 *
 * <p>No arma {@code <body>} ni {@code <ref-list>} completos — eso excede el
 * alcance de {@link JatsFrontBuilder}. La sección de referencias solo se usa
 * para fijar {@code ref-count}.
 */
public class DocxFrontParser {

    // =================================================================
    // Modelo de párrafo
    // =================================================================

    /** Un "run" de texto de Word con su formato relevante para el parseo. */
    private record Run(String text, boolean bold, boolean italic, boolean superscript, String hyperlink) {
    }

    private record Para(List<Run> runs, String text, boolean numbered) {

        /** true si TODOS los runs con texto son negrita (heurística de encabezado de sección). */
        boolean isFullyBold() {
            boolean any = false;
            for (Run r : runs) {
                if (r.text().isBlank()) continue;
                any = true;
                if (!r.bold()) return false;
            }
            return any;
        }

        boolean isBlank() {
            return text == null || text.isBlank();
        }
    }

    // =================================================================
    // Patrones (regex)
    // =================================================================

    private static final Pattern DOI = Pattern.compile("^10\\.\\d{4,9}/\\S+$");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern AFF_LABEL_PREFIX = Pattern.compile("^(\\d+)(.*)$", Pattern.DOTALL);
    private static final Pattern DEPARTAMENTO = Pattern.compile("Departamento de ([^,.;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROGRAMA = Pattern.compile("Programa de P[oó]s-?[Gg]radua[cç][aã]o em ([^,.;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAINEE_ROLE_BEFORE_PROGRAM = Pattern.compile(
            "(?:Posdoctorand[ao]|Doctorand[ao]|Estudiante|Maestrand[ao]|Alumn[ao])\\s*,\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTITUCION = Pattern.compile(
            "(Universidade|Universidad|Universit[eé]|Facultad|Faculdade|Instituto)\\s+[^,.;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_COUNTRY_TAIL = Pattern.compile(",\\s*([^,]+),\\s*([^,.]+)\\.\\s*$");
    private static final Pattern FUNDING_AWARD_ID = Pattern.compile("No\\.?\\s*([\\w./-]+)");
    private static final Pattern FUNDING_INSTITUTION = Pattern.compile(
            "(?:de la|del|de)\\s+([A-ZÀ-Ý][^,]*?(?:\\([A-Z]{2,}\\))?)(?:,|$)");
    private static final Pattern HISTORY_LINE = Pattern.compile(
            "^(Recibido|Versi[oó]n final|Aprobado)\\s*:\\s*(\\d{1,2})\\s+([a-zA-Zé]+)\\.?\\s+(\\d{4})\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> MESES_ES = new LinkedHashMap<>();
    static {
        MESES_ES.put("ene", "01"); MESES_ES.put("feb", "02"); MESES_ES.put("mar", "03");
        MESES_ES.put("abr", "04"); MESES_ES.put("may", "05"); MESES_ES.put("jun", "06");
        MESES_ES.put("jul", "07"); MESES_ES.put("ago", "08"); MESES_ES.put("sep", "09");
        MESES_ES.put("oct", "10"); MESES_ES.put("nov", "11"); MESES_ES.put("dic", "12");
    }

    // Traducción de nombre de país a la forma que usa la revista en el XML (inglés).
    private static final Map<String, String> COUNTRY_DISPLAY_EN = new LinkedHashMap<>();
    static {
        COUNTRY_DISPLAY_EN.put("brasil", "Brazil");
        COUNTRY_DISPLAY_EN.put("argentina", "Argentina");
        COUNTRY_DISPLAY_EN.put("méxico", "Mexico");
        COUNTRY_DISPLAY_EN.put("mexico", "Mexico");
        COUNTRY_DISPLAY_EN.put("chile", "Chile");
        COUNTRY_DISPLAY_EN.put("colombia", "Colombia");
        COUNTRY_DISPLAY_EN.put("uruguay", "Uruguay");
        COUNTRY_DISPLAY_EN.put("paraguay", "Paraguay");
        COUNTRY_DISPLAY_EN.put("perú", "Peru");
        COUNTRY_DISPLAY_EN.put("peru", "Peru");
        COUNTRY_DISPLAY_EN.put("bolivia", "Bolivia");
        COUNTRY_DISPLAY_EN.put("ecuador", "Ecuador");
        COUNTRY_DISPLAY_EN.put("venezuela", "Venezuela");
        COUNTRY_DISPLAY_EN.put("españa", "Spain");
        COUNTRY_DISPLAY_EN.put("estados unidos", "United States");
    }

    // Encabezados fijos que delimitan secciones del cierre del artículo.
    private static final String H_FUNDING = "Financiamiento";
    private static final String H_CONFLICT = "Conflicto de Intereses";
    private static final String H_CONTRIB = "Contribución autoral";
    private static final String H_REFERENCES = "Referencias bibliográficas";

    // =================================================================
    // Resultado auxiliar (para inspección/depuración fuera del builder)
    // =================================================================

    public record ParseWarning(String section, String message) {
    }

    private final List<ParseWarning> warnings = new ArrayList<>();

    public List<ParseWarning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    private void warn(String section, String message) {
        warnings.add(new ParseWarning(section, message));
    }

    // =================================================================
    // Límites de párrafo consumidos por el front (para que quien construya
    // <body>/<back> con otro recorrido del mismo documento sepa qué rango
    // saltear y no duplicar contenido).
    // =================================================================

    private int bodyStartParaIndex = -1;
    private int bodyEndParaIndex = -1;

    /** Índice (0-based, solo párrafos &lt;w:p&gt; de &lt;w:body&gt;, sin contar tablas) del primer
     *  párrafo que pertenece al cuerpo del artículo (justo después de las keywords en inglés). */
    public int getBodyStartParaIndex() {
        return bodyStartParaIndex;
    }

    /** Índice (exclusivo) donde termina el cuerpo y arranca el cierre
     *  (Financiamiento / Conflicto de Intereses / Contribución autoral / Referencias / historia). */
    public int getBodyEndParaIndex() {
        return bodyEndParaIndex;
    }

    // =================================================================
    // Entrada pública
    // =================================================================

    public JatsFrontBuilder parse(File docxFile) throws Exception {
        List<Para> paras = extractParagraphs(docxFile);
        JatsFrontBuilder b = new JatsFrontBuilder();
        Cursor c = new Cursor(paras);

        parseJournalMeta(c, b);
        parseArticleCategory(c, b);
        parseTitle(c, b);
        parseTransTitle(c, b);
        List<AuthorRef> authorRefs = parseAuthors(c);
        AffiliationParseResult affResult = parseAffiliations(c, b);
        linkAuthors(b, authorRefs, affResult);
        parseAbstractAndKeywords(c, b);
        bodyStartParaIndex = c.i;
        skipBodyUntilBackMatter(c);
        bodyEndParaIndex = c.i;
        parseBackMatter(c, b);
        applyJournalWideDefaults(b);

        return b;
    }

    // =================================================================
    // Cursor sobre la lista de párrafos
    // =================================================================

    private static class Cursor {
        final List<Para> paras;
        int i = 0;

        Cursor(List<Para> paras) {
            this.paras = paras;
        }

        boolean hasNext() {
            return i < paras.size();
        }

        Para peek() {
            return hasNext() ? paras.get(i) : null;
        }

        Para next() {
            return paras.get(i++);
        }

        /** Devuelve el próximo párrafo no vacío, saltando blancos, o null si se acabó. */
        Para nextNonBlank() {
            while (hasNext()) {
                Para p = next();
                if (!p.isBlank()) return p;
            }
            return null;
        }
    }

    // =================================================================
    // Estado: metadatos de revista (posicionales, fijos)
    // =================================================================

    private void parseJournalMeta(Cursor c, JatsFrontBuilder b) {
        Para first = c.nextNonBlank();
        if (first == null) return;

        String firstText = textOf(first);
        if (DOI.matcher(firstText).matches()) {
            // Algunos DOCX arrancan directamente con DOI/categoría/título y no
            // incluyen el bloque fijo de journal-meta.
            b.setArticleIdDoi(firstText);
            return;
        }

        String spsVersion = firstText;
        String lang = textOf(c.nextNonBlank());
        String journalAbbrev = textOf(c.nextNonBlank());
        String journalId = textOf(c.nextNonBlank());
        String journalName = textOf(c.nextNonBlank());
        String journalAbbrevTitle = textOf(c.nextNonBlank()); // normalmente igual a journalAbbrev
        String issnPrint = textOf(c.nextNonBlank());
        String issnElectronic = textOf(c.nextNonBlank());
        String publisherName = textOf(c.nextNonBlank());
        String doiCandidate = textOf(c.nextNonBlank());

        b.setSpsVersion(spsVersion);
        b.setLang(lang);
        b.setJournalAbbrev(journalAbbrev);
        b.setJournalId(journalId);
        b.setJournalName(journalName);
        if (notBlank(journalAbbrevTitle)) b.setJournalAbbrev(journalAbbrevTitle);
        b.setIssnPrint(issnPrint);
        b.setIssnElectronic(issnElectronic);
        b.setPublisherName(publisherName);

        if (doiCandidate != null && DOI.matcher(doiCandidate).matches()) {
            b.setArticleIdDoi(doiCandidate);
        } else {
            warn("journal-meta", "No se encontró un DOI con el formato esperado en la posición fija; "
                    + "valor visto: '" + doiCandidate + "'. Revisar manualmente article-id.");
            // Si no matcheó, probablemente ese párrafo ya es la categoría/título:
            // lo devolvemos al cursor retrocediendo un paso.
            if (doiCandidate != null) c.i--;
        }
    }

    // =================================================================
    // Estado: categoría del artículo ("Artículo")
    // =================================================================

    private void parseArticleCategory(Cursor c, JatsFrontBuilder b) {
        Para p = c.nextNonBlank();
        if (p != null && p.isFullyBold()) {
            b.setArticleCategory(p.text().trim());
        } else if (p != null) {
            warn("article-category", "Se esperaba una categoría en negrita (ej. 'Artículo'); "
                    + "se usó el valor por defecto y se devolvió el párrafo al título.");
            c.i--;
        }
    }

    // =================================================================
    // Estado: título / trans-título
    // =================================================================

    private void parseTitle(Cursor c, JatsFrontBuilder b) {
        Para p = c.nextNonBlank();
        if (p == null) return;
        b.setTitle(renderInline(p.runs()));
    }

    private void parseTransTitle(Cursor c, JatsFrontBuilder b) {
        Para p = c.peek();
        if (p == null || p.isBlank()) return;
        if (looksLikeAuthorLine(p)) return; // no había trans-title, ya llegamos a autores
        c.next();
        b.setTransTitle(renderInline(p.runs()));
    }

    // =================================================================
    // Estado: autores
    // =================================================================

    private static final Pattern BARE_ORCID = Pattern.compile("\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]");

    private record AuthorRef(String name, String orcid, List<String> affLabels) {
    }

    private record AffiliationParseResult(Map<String, String> idByLabel, Map<String, String> bioByLabel) {
    }

    /** ORCID hipervinculado (ideal) o, si el .docx no lo linkeó, un ID con forma de ORCID como texto plano. */
    private boolean looksLikeAuthorLine(Para p) {
        if (p == null || p.isBlank()) return false;
        for (Run r : p.runs()) {
            if (r.hyperlink() != null && r.hyperlink().contains("orcid.org")) return true;
            if (!r.superscript() && BARE_ORCID.matcher(r.text().trim()).matches()) return true;
        }
        boolean sawBaseText = false;
        boolean sawSupAfterBase = false;
        for (Run r : p.runs()) {
            if (r.text().isBlank()) continue;
            if (r.superscript()) {
                if (sawBaseText) sawSupAfterBase = true;
            } else {
                sawBaseText = true;
            }
        }
        return sawSupAfterBase;
    }

    private List<AuthorRef> parseAuthors(Cursor c) {
        List<AuthorRef> authors = new ArrayList<>();
        while (c.hasNext()) {
            Para p = c.peek();
            if (p.isBlank()) {
                c.next();
                continue;
            }
            if (!looksLikeAuthorLine(p)) break;
            c.next();
            authors.add(parseAuthorLine(p));
        }
        return authors;
    }

    private AuthorRef parseAuthorLine(Para p) {
        StringBuilder name = new StringBuilder();
        StringBuilder supDigits = new StringBuilder();
        String orcid = null;
        for (Run r : p.runs()) {
            if (r.text().isBlank()) continue;
            String trimmed = r.text().trim();
            if (r.hyperlink() != null && r.hyperlink().contains("orcid.org")) {
                orcid = r.hyperlink();
            } else if (!r.superscript() && BARE_ORCID.matcher(trimmed).matches()) {
                // ORCID escrito como texto plano, sin hipervínculo: se guarda tal
                // cual está en el .docx (sin forzar el prefijo https://orcid.org/,
                // porque esta revista no siempre lo usa de forma consistente).
                orcid = trimmed;
            } else if (r.superscript()) {
                supDigits.append(r.text());
            } else if (orcid == null) {
                // texto normal antes del orcid: nombre del autor
                name.append(r.text());
            }
        }
        List<String> labels = new ArrayList<>();
        for (String part : supDigits.toString().split("[,\\s]+")) {
            if (!part.isBlank()) labels.add(part.trim());
        }
        return new AuthorRef(name.toString().trim(), orcid, labels);
    }

    // =================================================================
    // Estado: afiliaciones
    // =================================================================

    private boolean looksLikeAffiliationLine(Para p) {
        if (p.runs().isEmpty()) return false;
        boolean startsWithSup = false;
        for (Run r : p.runs()) {
            if (r.text().isBlank()) continue;
            startsWithSup = r.superscript();
            break;
        }
        if (!startsWithSup) return false;
        boolean hasEmail = EMAIL.matcher(p.text()).find();
        boolean hasMailto = p.runs().stream().anyMatch(r -> r.hyperlink() != null && r.hyperlink().startsWith("mailto:"));
        return hasEmail || hasMailto;
    }

    /** @return mapa label ("1","2",...) -&gt; id de aff generado ("aff1","aff2",...) en orden de aparición. */
    private AffiliationParseResult parseAffiliations(Cursor c, JatsFrontBuilder b) {
        Map<String, String> idByLabel = new LinkedHashMap<>();
        Map<String, String> bioByLabel = new LinkedHashMap<>();
        int n = 0;
        while (c.hasNext()) {
            Para p = c.peek();
            if (p.isBlank()) {
                c.next();
                continue;
            }
            if (!looksLikeAffiliationLine(p)) break;
            c.next();
            n++;
            String id = "aff" + n;
            String label = extractLabel(p);
            idByLabel.put(label, id);
            String bio = appendAffiliationFromParagraph(b, id, label, p);
            if (notBlank(bio)) bioByLabel.put(label, bio);
        }
        return new AffiliationParseResult(idByLabel, bioByLabel);
    }

    private String extractLabel(Para p) {
        StringBuilder sup = new StringBuilder();
        for (Run r : p.runs()) {
            if (r.text().isBlank()) continue;
            if (r.superscript()) sup.append(r.text());
            else break;
        }
        return sup.toString().trim();
    }

    private String appendAffiliationFromParagraph(JatsFrontBuilder b, String id, String label, Para p) {
        String fullText = p.text().trim();
        Matcher m = AFF_LABEL_PREFIX.matcher(fullText);
        String body = m.matches() ? m.group(2).trim() : fullText;

        Matcher emailM = EMAIL.matcher(body);
        String email = emailM.find() ? emailM.group() : null;
        String bodyNoEmail = email != null ? body.substring(0, emailM.start()).trim() : body;

        String original = bodyNoEmail;

        Matcher deptM = DEPARTAMENTO.matcher(bodyNoEmail);
        Matcher progM = PROGRAMA.matcher(bodyNoEmail);
        String dept = deptM.find() ? "Departamento de " + deptM.group(1).trim() : null;
        String prog = null;
        if (progM.find()) {
            // Si el "Programa de..." aparece justo después de un rol de
            // estudiante/becario (posdoctoranda, doctorando, etc.), es su
            // afiliación como alumno/a, no un cargo docente/institucional:
            // el patrón de la revista no lo cuenta como orgdiv en ese caso.
            String before = bodyNoEmail.substring(0, progM.start());
            if (!TRAINEE_ROLE_BEFORE_PROGRAM.matcher(before).find()) {
                prog = "Programa de Pós-Graduação em " + progM.group(1).trim();
            } else {
                warn("aff:" + id, "Se encontró 'Programa de...' precedido por un rol de estudiante/becario; "
                        + "no se promovió a orgdiv (queda solo en 'original'). Revisar si corresponde.");
            }
        }

        // Convención observada en el patrón de la revista: si hay departamento
        // Y programa, el programa va como orgdiv1 y el departamento como
        // orgdiv2; si hay uno solo, ese va como orgdiv1.
        String orgdiv1 = null, orgdiv2 = null;
        if (prog != null && dept != null) {
            orgdiv1 = prog;
            orgdiv2 = dept;
        } else if (prog != null) {
            orgdiv1 = prog;
        } else if (dept != null) {
            orgdiv1 = dept;
        }

        String orgname = outerInstitution(bodyNoEmail);
        String bio = extractBioPrefix(bodyNoEmail, orgname);

        String city = null, state = null, country = null;
        Matcher tailM = LOCATION_COUNTRY_TAIL.matcher(bodyNoEmail);
        if (tailM.find()) {
            String location = tailM.group(1).trim();
            country = tailM.group(2).trim();
            String display = COUNTRY_DISPLAY_EN.get(country.toLowerCase());
            if (display != null) country = display;

            String countryKey = country.toLowerCase();
            if (countryKey.equals("brasil") || countryKey.equals("brazil")) {
                state = location;
            } else if (countryKey.equals("argentina")) {
                city = location;
            } else {
                // En otros países priorizamos el nivel urbano porque suele ser
                // el dato más estable en la afiliación del patrón de esta revista.
                city = location;
            }
        } else {
            warn("aff:" + id, "No se pudo separar estado/país al final de la afiliación; texto: '"
                    + bodyNoEmail + "'.");
        }

        if (orgname == null) {
            warn("aff:" + id, "No se identificó una institución (Universidade/Universidad/...) en el texto; "
                    + "revisar 'normalized'/'orgname' manualmente.");
        }

        if (orgdiv1 == null) {
            String firstInstitution = firstInstitution(bodyNoEmail);
            if (firstInstitution != null && !firstInstitution.equals(orgname)) {
                orgdiv1 = firstInstitution;
            }
        }

        b.appendAffiliation(id, label, original, orgname, orgdiv1, orgdiv2, orgname, city, state, country, email);
        return bio;
    }

    private String extractBioPrefix(String text, String orgname) {
        if (text == null || text.isBlank()) return null;
        String candidate = text;
        if (orgname != null && !orgname.isBlank()) {
            int idx = text.indexOf(orgname);
            if (idx > 0) candidate = text.substring(0, idx).trim();
        } else {
            Matcher m = INSTITUCION.matcher(text);
            if (m.find()) {
                int idx = m.start();
                if (idx > 0) candidate = text.substring(0, idx).trim();
            }
        }

        if (candidate.isBlank()) return null;
        if (!candidate.matches("(?i)^(Doctora?|Doctor|Mag[íi]ster|M[aá]ster|Posdoctorand[ao]|Doctorand[ao]|Estudiante|Licenciado|Licenciada|Ingeniero|Ingeniera|M[eé]dico|M[eé]dica|Profesor[a]?|Docente|Investigador[a]?|Director[a]?)\\b.*")) {
            return null;
        }
        return candidate.replaceAll("[\s,.;:-]+$", "");
    }

    private String outerInstitution(String text) {
        Matcher m = INSTITUCION.matcher(text);
        String last = null;
        while (m.find()) {
            last = m.group().trim();
        }
        return last;
    }

    private String firstInstitution(String text) {
        Matcher m = INSTITUCION.matcher(text);
        return m.find() ? m.group().trim() : null;
    }

    private void linkAuthors(JatsFrontBuilder b, List<AuthorRef> authorRefs, AffiliationParseResult affResult) {
        for (AuthorRef a : authorRefs) {
            List<String> rids = new ArrayList<>();
            String bio = null;
            for (String label : a.affLabels()) {
                String rid = affResult.idByLabel().get(label);
                if (rid != null) {
                    rids.add(rid);
                    if (bio == null) bio = affResult.bioByLabel().get(label);
                } else {
                    warn("authors", "El autor '" + a.name() + "' referencia la afiliación '" + label
                            + "' pero no se encontró esa afiliación en el documento.");
                }
            }
            b.appendAuthor(a.name(), rids, a.orcid(), null, bio, false, null, false);
        }
    }

    // =================================================================
    // Estado: resumen/abstract y palabras clave
    // =================================================================

    private static final Pattern LABEL_RESUMEN = Pattern.compile("^Resumen$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_ABSTRACT = Pattern.compile("^Abstract$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_KWD_ES = Pattern.compile("^Palabras\\s+claves?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_KWD_EN = Pattern.compile("^Keywords?$", Pattern.CASE_INSENSITIVE);

    private void parseAbstractAndKeywords(Cursor c, JatsFrontBuilder b) {
        parseLabeledParagraph(c, LABEL_RESUMEN, b::setAbstractTitle,
                (runsAfterLabel) -> b.appendAbstractParagraph(renderInline(runsAfterLabel)));
        parseLabeledKeywords(c, LABEL_KWD_ES, b::setKeywordsTitleEs, b::addKeywordEs);
        parseLabeledParagraph(c, LABEL_ABSTRACT, b::setTransAbstractTitle,
                (runsAfterLabel) -> b.appendTransAbstractParagraph(renderInline(runsAfterLabel)));
        parseLabeledKeywords(c, LABEL_KWD_EN, b::setKeywordsTitleEn, b::addKeywordEn);
    }

    private interface RunsConsumer {
        void accept(List<Run> runs);
    }

    private record LabelMatch(int index, String labelText) {
    }

    private void parseLabeledParagraph(Cursor c, Pattern label, java.util.function.Consumer<String> titleSetter,
                                        RunsConsumer consumer) {
        Para p = c.nextNonBlank();
        if (p == null) return;
        List<Run> runs = p.runs();
        LabelMatch match = findLabelSplit(runs, label);
        if (match == null) {
            warn("abstract/keywords", "No se encontró la etiqueta esperada (" + label.pattern()
                    + ") en el párrafo: '" + p.text() + "'.");
            return;
        }
        // Se preserva el texto de la etiqueta tal como está en el .docx (mayúsculas,
        // dos puntos, etc.) en vez de forzar un texto fijo: distintos artículos de
        // esta misma revista lo escriben distinto ("Resumen" vs "RESUMEN").
        // A diferencia de kwd-group, el título de <abstract>/<trans-abstract> en
        // el patrón de la revista NO lleva los dos puntos, sin importar si en el
        // .docx el ':' quedó dentro o fuera de la negrita (es inconsistente entre
        // "Resumen" y "Abstract:" en el mismo documento).
        titleSetter.accept(match.labelText().replaceAll(":+$", "").trim());
        consumer.accept(stripLeadingColonAndSpace(runs.subList(match.index(), runs.size())));
    }

    private void parseLabeledKeywords(Cursor c, Pattern label, java.util.function.Consumer<String> titleSetter,
                                       java.util.function.Consumer<String> adder) {
        Para p = c.nextNonBlank();
        if (p == null) return;
        List<Run> runs = p.runs();
        LabelMatch match = findLabelSplit(runs, label);
        if (match == null) {
            warn("keywords", "No se encontró la etiqueta esperada (" + label.pattern() + ").");
            return;
        }
        titleSetter.accept(labelWithColon(match, runs));
        String rest = renderInline(stripLeadingColonAndSpace(runs.subList(match.index(), runs.size())));
        for (String kw : rest.split(";")) {
            String trimmed = kw.trim();
            if (trimmed.endsWith(".")) trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            if (!trimmed.isEmpty()) adder.accept(trimmed);
        }
    }

    /** Si el ':' del label quedó en el primer run NO negrita (en vez de dentro del negrita), lo suma igual:
     *  visualmente es la misma etiqueta para quien lee el .docx. */
    private String labelWithColon(LabelMatch match, List<Run> runs) {
        String label = match.labelText();
        if (label.endsWith(":")) return label;
        if (match.index() < runs.size()) {
            String next = runs.get(match.index()).text();
            if (next.startsWith(":")) return label + ":";
        }
        return label;
    }

    /** Busca el primer run negrita cuyo texto acumulado (con los siguientes negrita) matchee la etiqueta.
     *  El texto de la etiqueta que devuelve conserva mayúsculas/dos puntos tal como están en el .docx
     *  (solo recorta espacios en los bordes); el regex de matcheo, en cambio, ignora un ':' final para
     *  poder reconocer tanto "Resumen" como "Resumen:" como la misma etiqueta. */
    private LabelMatch findLabelSplit(List<Run> runs, Pattern label) {
        StringBuilder acc = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            Run r = runs.get(i);
            if (!r.bold()) {
                String rawLabel = acc.toString().trim();
                String forMatch = rawLabel.replaceAll(":+$", "").trim();
                if (label.matcher(forMatch).matches()) return new LabelMatch(i, rawLabel);
                return null;
            }
            acc.append(r.text());
        }
        return null;
    }

    private List<Run> stripLeadingColonAndSpace(List<Run> runs) {
        List<Run> out = new ArrayList<>(runs);
        while (!out.isEmpty()) {
            Run first = out.get(0);
            String trimmed = first.text().replaceFirst("^[:\\s]+", "");
            if (trimmed.equals(first.text())) break;
            if (trimmed.isEmpty()) {
                out.remove(0);
            } else {
                out.set(0, new Run(trimmed, first.bold(), first.italic(), first.superscript(), first.hyperlink()));
                break;
            }
        }
        return out;
    }

    // =================================================================
    // Cuerpo (se omite del front; solo se avanza el cursor)
    // =================================================================

    private void skipBodyUntilBackMatter(Cursor c) {
        while (c.hasNext()) {
            Para p = c.peek();
            if (!p.isBlank() && p.isFullyBold()) {
                String t = p.text().trim();
                if (isBackMatterHeading(t)) {
                    return; // dejamos el heading sin consumir para el siguiente estado
                }
            }
            c.next();
        }
    }

    // =================================================================
    // Cierre: financiamiento, conflicto, contribución, referencias, historia
    // =================================================================

    private void parseBackMatter(Cursor c, JatsFrontBuilder b) {
        while (c.hasNext()) {
            Para p = c.peek();
            if (p.isBlank()) {
                c.next();
                continue;
            }
            String t = p.text().trim();
            if (p.isFullyBold() && t.equalsIgnoreCase(H_FUNDING)) {
                c.next();
                parseFunding(c, b);
            } else if (p.isFullyBold() && t.equalsIgnoreCase(H_CONFLICT)) {
                c.next();
                parseConflict(c, b);
            } else if (p.isFullyBold() && t.equalsIgnoreCase(H_CONTRIB)) {
                c.next();
                parseContribution(c, b);
            } else if (p.isFullyBold() && t.equalsIgnoreCase(H_REFERENCES)) {
                c.next();
                parseReferences(c, b);
            } else if (HISTORY_LINE.matcher(t).matches()) {
                parseHistory(c, b);
            } else {
                c.next(); // texto que no reconocemos entre secciones de cierre; se ignora
            }
        }
    }

    private List<String> collectUntilNextHeading(Cursor c) {
        List<String> out = new ArrayList<>();
        while (c.hasNext()) {
            Para p = c.peek();
            if (p.isBlank()) {
                c.next();
                continue;
            }
            if (p.isFullyBold()) {
                String t = p.text().trim();
                if (isBackMatterHeading(t)
                        || HISTORY_LINE.matcher(t).matches()) {
                    break;
                }
            }
            out.add(p.text().trim());
            c.next();
        }
        return out;
    }

    private void parseFunding(Cursor c, JatsFrontBuilder b) {
        List<String> lines = collectUntilNextHeading(c);
        String fullStatement = String.join(" ", lines);
        if (fullStatement.isBlank()) return;
        b.setFundingStatement(fullStatement);

        for (String clause : fullStatement.split(";\\s*")) {
            Matcher awardM = FUNDING_AWARD_ID.matcher(clause);
            String awardId = awardM.find() ? awardM.group(1).replaceAll("\\.$", "") : null;
            List<String> instMatches = new ArrayList<>();
            Matcher instM = FUNDING_INSTITUTION.matcher(clause);
            while (instM.find()) instMatches.add(instM.group(1).trim());
            String institution = instMatches.isEmpty() ? null : instMatches.get(instMatches.size() - 1);
            if (institution != null) {
                b.addFundingSource(institution, awardId, "contract");
            } else {
                warn("funding", "No se pudo identificar la institución financiadora en: '" + clause + "'.");
            }
        }
    }

    private void parseConflict(Cursor c, JatsFrontBuilder b) {
        List<String> lines = collectUntilNextHeading(c);
        // El patrón de la revista arma un único <p> con todo el texto de la
        // sección (no un <p> por línea del .docx).
        b.addAuthorNoteFn("conflict", "fn2", "Conflicto de Intereses", String.join(" ", lines));
    }

    private void parseContribution(Cursor c, JatsFrontBuilder b) {
        List<String> lines = collectUntilNextHeading(c);
        String contribution = String.join(" ", lines);
        for (String authorContribution : contribution.split("(?<=\\.)\\s+(?=[^:]+:)", -1)) {
            int separator = authorContribution.indexOf(':');
            if (separator < 1) continue;
            String authorName = authorContribution.substring(0, separator).trim();
            String role = authorContribution.substring(separator + 1).trim();
            b.setAuthorRole(authorName, role);
        }
    }

    private void parseReferences(Cursor c, JatsFrontBuilder b) {
        int count = 0;
        while (c.hasNext()) {
            Para p = c.peek();
            if (p.isBlank()) {
                c.next();
                continue;
            }
            if (p.isFullyBold()) break; // siguiente heading (ej. no debería haber, pero por robustez)
            if (HISTORY_LINE.matcher(p.text().trim()).matches()) break;
            if (p.numbered()) count++;
            c.next();
        }
        b.setRefCount(count);
    }

    private boolean isBackMatterHeading(String text) {
        return text.equalsIgnoreCase(H_FUNDING)
                || text.equalsIgnoreCase(H_CONFLICT)
                || text.equalsIgnoreCase(H_CONTRIB)
                || text.equalsIgnoreCase(H_REFERENCES);
    }

    private void parseHistory(Cursor c, JatsFrontBuilder b) {
        while (c.hasNext()) {
            Para p = c.peek();
            if (p.isBlank()) {
                c.next();
                continue;
            }
            Matcher m = HISTORY_LINE.matcher(p.text().trim());
            if (!m.matches()) break;
            c.next();
            String type = m.group(1).toLowerCase();
            String day = String.format("%02d", Integer.parseInt(m.group(2)));
            String mesTxt = m.group(3).toLowerCase().substring(0, Math.min(3, m.group(3).length()));
            String month = MESES_ES.getOrDefault(mesTxt, "");
            String year = m.group(4);
            if (month.isEmpty()) {
                warn("history", "No se reconoció el mes '" + m.group(3) + "' en la línea de historia.");
            }
            if (type.startsWith("recibido")) {
                b.setHistoryReceived(day, month, year);
            } else if (type.startsWith("versión final") || type.startsWith("version final")) {
                b.setHistoryRevRecd(day, month, year);
            } else if (type.startsWith("aprobado")) {
                b.setHistoryAccepted(day, month, year);
            }
        }
    }

    // =================================================================
    // Defaults que no vienen en el .docx (política fija de la revista)
    // =================================================================

    /**
     * La licencia (CC BY 4.0) es una política editorial fija de la revista,
     * no un dato del manuscrito: no aparece en ningún párrafo del .docx.
     * Se aplica como default y se deja constancia en {@link #warnings} para
     * que quede explícito que no fue "leído" sino asumido. {@code volume},
     * {@code issue}, {@code elocation-id} y el {@code pub-date} de
     * publicación tampoco están en el .docx (los asigna el sistema editorial
     * al momento de publicar): quedan a cargo de quien llame a este parser,
     * vía {@code builder.setVolume(...)}, etc., después de {@link #parse}.
     */
    private void applyJournalWideDefaults(JatsFrontBuilder b) {
        b.setLicense(
                "https://creativecommons.org/licenses/by/4.0/",
                "Este es un artículo publicado en acceso abierto bajo una licencia Creative Commons",
                "open-access", "es");
        warn("permissions", "La licencia no está en el .docx; se aplicó el valor fijo de la revista "
                + "(CC BY 4.0, es). Ajustar si esta revista cambia de política o el artículo es una excepción.");
        warn("article-meta", "volume / issue / elocation-id / pub-date de publicación no están en el .docx "
                + "(los define el sistema editorial al publicar). Setearlos aparte con "
                + "builder.setVolume(...), setElocationId(...), setPubDate(...).");
    }

    // =================================================================
    // Render inline (runs -> XML)
    // =================================================================

    private String renderInline(List<Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (Run r : runs) {
            if (r.text().isEmpty()) continue;
            String escaped = XmlUtils.escape(r.text());
            if (r.italic()) escaped = "<italic>" + escaped + "</italic>";
            if (r.superscript()) escaped = "<sup>" + escaped + "</sup>";
            sb.append(escaped);
        }
        return sb.toString().trim();
    }

    private String textOf(Para p) {
        return p == null ? null : p.text().trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // =================================================================
    // Extracción de párrafos desde word/document.xml
    // =================================================================

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private List<Para> extractParagraphs(File docxFile) throws Exception {
        Map<String, String> rels = new LinkedHashMap<>();
        Document relsDoc = readEntryAsXml(docxFile, "word/_rels/document.xml.rels");
        if (relsDoc != null) {
            NodeList relNodes = relsDoc.getElementsByTagName("Relationship");
            for (int i = 0; i < relNodes.getLength(); i++) {
                Element el = (Element) relNodes.item(i);
                rels.put(el.getAttribute("Id"), el.getAttribute("Target"));
            }
        }

        Document doc = readEntryAsXml(docxFile, "word/document.xml");
        if (doc == null) throw new IllegalArgumentException("word/document.xml no encontrado en " + docxFile);

        NodeList bodyList = doc.getElementsByTagNameNS(W_NS, "body");
        if (bodyList.getLength() == 0) throw new IllegalArgumentException("El documento no tiene <w:body>");
        Element body = (Element) bodyList.item(0);

        List<Para> paras = new ArrayList<>();
        NodeList children = body.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            if (!"p".equals(el.getLocalName())) continue;
            paras.add(parseParagraph(el, rels));
        }
        return paras;
    }

    private Para parseParagraph(Element p, Map<String, String> rels) {
        boolean numbered = hasNumPr(p);
        List<Run> runs = new ArrayList<>();
        collectRuns(p, null, rels, runs);
        StringBuilder text = new StringBuilder();
        for (Run r : runs) text.append(r.text());
        return new Para(runs, text.toString(), numbered);
    }

    private boolean hasNumPr(Element p) {
        NodeList pPrList = p.getElementsByTagNameNS(W_NS, "pPr");
        if (pPrList.getLength() == 0) return false;
        Element pPr = (Element) pPrList.item(0);
        // Solo directo (no de párrafos hijos, que no aplica en w:p)
        for (int i = 0; i < pPr.getChildNodes().getLength(); i++) {
            Node n = pPr.getChildNodes().item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "numPr".equals(((Element) n).getLocalName())) return true;
        }
        return false;
    }

    private void collectRuns(Element parent, String hyperlinkTarget, Map<String, String> rels, List<Run> out) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            String local = el.getLocalName();
            if ("hyperlink".equals(local)) {
                String rid = el.getAttributeNS(R_NS, "id");
                String target = rels.get(rid);
                collectRuns(el, target, rels, out);
            } else if ("r".equals(local)) {
                out.add(parseRun(el, hyperlinkTarget));
            }
        }
    }

    private Run parseRun(Element r, String hyperlinkTarget) {
        StringBuilder text = new StringBuilder();
        boolean bold = false, italic = false, superscript = false;
        NodeList children = r.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            String local = el.getLocalName();
            if ("rPr".equals(local)) {
                NodeList rprChildren = el.getChildNodes();
                for (int j = 0; j < rprChildren.getLength(); j++) {
                    Node rn = rprChildren.item(j);
                    if (rn.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element re = (Element) rn;
                    switch (re.getLocalName()) {
                        case "b" -> {
                            if (!"false".equals(re.getAttributeNS(W_NS, "val")) && !"0".equals(re.getAttributeNS(W_NS, "val"))) {
                                bold = true;
                            }
                        }
                        case "i" -> {
                            if (!"false".equals(re.getAttributeNS(W_NS, "val")) && !"0".equals(re.getAttributeNS(W_NS, "val"))) {
                                italic = true;
                            }
                        }
                        case "vertAlign" -> {
                            if ("superscript".equals(re.getAttributeNS(W_NS, "val"))) superscript = true;
                        }
                        default -> {
                        }
                    }
                }
            } else if ("t".equals(local)) {
                text.append(el.getTextContent());
            } else if ("tab".equals(local)) {
                text.append('\t');
            }
        }
        return new Run(text.toString(), bold, italic, superscript, hyperlinkTarget);
    }

    private Document readEntryAsXml(File docxFile, String entryName) throws Exception {
        try (ZipFile zip = new ZipFile(docxFile)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) return null;
            try (InputStream is = zip.getInputStream(entry)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                // Endurecido contra XXE: el .docx puede venir de un tercero no confiable.
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setExpandEntityReferences(false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                return builder.parse(is);
            }
        }
    }
}
