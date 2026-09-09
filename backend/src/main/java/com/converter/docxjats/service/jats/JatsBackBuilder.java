package com.converter.docxjats.service.jats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construye exhaustivamente el bloque {@code <back>} del artículo JATS conforme a SciELO PS,
 * incluyendo agradecimientos {@code <ack>}, notas del artículo {@code <fn-group>} con
 * clasificación de tipos (conflict, con, financial-disclosure, other), declaración de
 * disponibilidad de datos {@code <sec sec-type="data-availability">}, y referencias
 * bibliográficas {@code <ref-list>} con máxima granularidad estructural.
 * 
 * <p>Cada referencia se parsea mediante expresiones regulares y heurísticas para extraer:
 * <ul>
 *   <li>Autores/colaboradores con nombres y apellidos estructurados</li>
 *   <li>Título del artículo o fuente</li>
 *   <li>Datos de publicación (año, volumen, número, páginas, URL)</li>
 *   <li>Identificadores digitales (DOI, ISBN, ISSN)</li>
 *   <li>Información de editorial para libros/tesis</li>
 * </ul>
 * 
 * <p>Genera tanto {@code <mixed-citation>} (texto plano) como {@code <element-citation>}
 * (etiquetado estructurado) respetando en todo momento los atributos y criterios de SciELO PS.
 */
public class JatsBackBuilder {

    // ---------------------------------------------------------------
    // Patrones de Expresiones Regulares para Análisis de Referencias
    // ---------------------------------------------------------------

    /** Extrae el número de referencia opcional al inicio y el texto restante. */
    private static final Pattern REFERENCE_NUMBER = 
        Pattern.compile("^\\s*(?:\\[(\\d+)\\]|(\\d+))[.)]?\\s*(.*)$");

    /** Detecta DOI en formato estándar 10.XXXX/... */
    private static final Pattern DOI_PATTERN = 
        Pattern.compile("(?i)\\b(10\\.\\d{4,9}/[-._;()/:a-z0-9]+)");

    /** Detecta URLs http/https. */
    private static final Pattern URL_PATTERN = 
        Pattern.compile("https?://[^\\s]+");

    /** Detecta años de 4 dígitos (1900-2099). */
    private static final Pattern YEAR_PATTERN = 
        Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");

    /** Detecta ISBN (isbn-10 o isbn-13). */
    private static final Pattern ISBN_PATTERN = 
        Pattern.compile("(?i)isbn[\\s\\-]?(?:\\d[\\s\\-]?){9}[\\d\\-xX]");

    /** Patrón para artículos de revista estructurado: Autores. Título. Fuente. Año;vol(iss):pag. */
    private static final Pattern JOURNAL_PATTERN = Pattern.compile(
        "^(.*?)\\.\\s+(.*?)\\.\\s+([^.;]+?)\\.\\s+(\\d{4});(\\d+)(?:\\((\\d+)\\))?:([\\d-]+|e\\d+).*$",
        Pattern.DOTALL
    );

    /** Patrón para libros: Autores. Título. Editorial; año. */
    private static final Pattern BOOK_PATTERN = Pattern.compile(
        "^(.*?)\\.\\s+(.*?)\\.\\s+([^;]+?);\\s+(\\d{4}).*$",
        Pattern.DOTALL
    );

    /** Patrón para tesis: Autor(es). Título. Tipo de Tesis, Institución; año. */
    private static final Pattern THESIS_PATTERN = Pattern.compile(
        "^(.*?)\\.\\s+(.*?)\\.\\s+((?:Tesis|Dissertation|Doctorado)[^,;]+)[;,]\\s+([^;]+);\\s+(\\d{4}).*$",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // ---------------------------------------------------------------
    // Tipos de Notas (fn-type) permitidos por SciELO PS
    // ---------------------------------------------------------------
    private enum FootnoteType {
        FINANCIAL_DISCLOSURE("financial-disclosure"),
        CONFLICT("conflict"),
        CONTRIBUTIONS("con"),
        OTHER("other"),
        EQUAL("equal");

        final String type;
        FootnoteType(String type) { this.type = type; }
    }

    // ---------------------------------------------------------------
    // Modelos Internos para Datos Estructurados
    // ---------------------------------------------------------------

    /** Representa un autor/colaborador dentro de una referencia. */
    private record Author(String surname, String givenNames) {}

    /** Agrupa autores como collab (organización). */
    private record Collaborator(String name) {}

    /** Estructura completa de una referencia bibliográfica. */
    private record Reference(
        String refId,
        String label,
        String citationText,
        String publicationType,
        List<Author> authors,
        List<Collaborator> collaborators,
        String articleTitle,
        String source,
        String year,
        String volume,
        String issue,
        String fpage,
        String lpage,
        String elocationId,
        String doi,
        String isbn,
        String url,
        String publisherName,
        String publisherLoc,
        String accessDate
    ) {}

    /** Representa una nota al pie del artículo clasificada. */
    private record FootnoteEntry(
        String fnType,
        String id,
        String label,
        String content
    ) {}

    // ---------------------------------------------------------------
    // Estado Interno del Builder
    // ---------------------------------------------------------------

    private final StringBuilder backBuilder = new StringBuilder();
    private final List<Reference> references = new ArrayList<>();
    private final Map<String, FootnoteEntry> footnotes = new LinkedHashMap<>();
    private String acknowledgementsText = null;
    private String dataAvailabilityText = null;
    private int refCounter = 0;
    private int footnoteCounter = 0;
    private boolean refListStarted = false;
    private String publisherLoc = null;
    private final RunRenderer runRenderer;

    /**
     * Constructor que recibe el renderizador de runs para mantener formato inline
     * (negrita, cursiva, etc.) en agradecimientos y notas.
     */
    public JatsBackBuilder(RunRenderer runRenderer) {
        this.runRenderer = runRenderer;
    }

    // ---------------------------------------------------------------
    // API Pública para Agregar Contenido a la Sección <back>
    // ---------------------------------------------------------------

    /**
     * Establece el texto de agradecimientos. Si se proporciona, se envuelve
     * en un nodo {@code <ack>} con su correspondiente {@code <title>}.
     * 
     * @param title    Título de la sección (ej. "Agradecimientos", "Acknowledgements")
     * @param content  Texto del párrafo de agradecimientos
     */
    public void setAcknowledgements(String title, String content) {
        if (content != null && !content.isBlank()) {
            acknowledgementsText = content.trim();
        }
    }

    /**
     * Agrega una nota al pie clasificada para el artículo.
     * Las notas se agruparán posteriormente dentro de {@code <fn-group>}.
     * 
     * @param fnType  Tipo de nota (financial-disclosure, conflict, con, other, equal)
     * @param label   Etiqueta visible (ej. "1", "*", "Conflicto")
     * @param content Contenido de la nota
     */
    public void addFootnote(String fnType, String label, String content) {
        if (content == null || content.isBlank()) return;
        
        footnoteCounter++;
        String id = "fn" + footnoteCounter;
        String normalizedType = normalizeFootnoteType(fnType);
        
        footnotes.put(id, new FootnoteEntry(normalizedType, id, label, content.trim()));
    }

    /**
     * Establece la declaración de disponibilidad de datos del artículo.
     * Se renderiza como {@code <sec sec-type="data-availability">}.
     * 
     * @param title   Título de la sección
     * @param content Contenido de la declaración
     */
    public void setDataAvailability(String title, String content) {
        if (content != null && !content.isBlank()) {
            dataAvailabilityText = content.trim();
        }
    }

    /**
     * Inicia la sección de referencias bibliográficas.
     * 
     * @param title Título a mostrar en {@code <ref-list>} (ej. "Referencias bibliográficas")
     */
    public void openReferences(String title) {
        if (refListStarted) {
            return;
        }
        refListStarted = true;
        refCounter = 0;
    }

    /**
     * Agrega una referencia bibliográfica completa al listado.
     * Parsea el texto mediante heurísticas y expresiones regulares para
     * identificar autores, año, volumen, páginas, DOI, URL, etc.
     * 
     * @param citationText Texto completo de la cita bibliográfica
     */
    public void appendReference(String citationText) {
        if (citationText == null || citationText.isBlank()) return;
        
        if (!refListStarted) {
            openReferences("Referencias bibliográficas");
        }

        // Extraer número de referencia si está presente
        Matcher numbered = REFERENCE_NUMBER.matcher(citationText);
        String refId;
        String cleanText;
        
        if (numbered.matches()) {
            String num = numbered.group(1) != null ? numbered.group(1) : numbered.group(2);
            refId = num != null ? num : String.valueOf(++refCounter);
            cleanText = numbered.group(3);
        } else {
            refCounter++;
            refId = String.valueOf(refCounter);
            cleanText = citationText.trim();
        }

        // Parsear la referencia completa
        Reference ref = parseReference(refId, cleanText);
        references.add(ref);
    }

    /**
     * Indica que no hay más referencias a agregar.
     * (Usado para cerrar la sección en el build final.)
     */
    public void closeReferences() {
        refListStarted = false;
    }

    /**
     * Verifica si hay contenido (referencias, notas, agradecimientos).
     */
    public boolean hasContent() {
        return !references.isEmpty() || !footnotes.isEmpty() || 
               acknowledgementsText != null || dataAvailabilityText != null;
    }

    /**
     * Construye y retorna el XML completo del bloque {@code <back>}.
     * El orden de generación es: agradecimientos, notas, datos disponibilidad, referencias.
     */
    public String build() {
        if (!hasContent()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        result.append("<back>\n");

        // 1. Agradecimientos
        if (acknowledgementsText != null) {
            result.append(buildAcknowledgements());
        }

        // 2. Notas del Artículo
        if (!footnotes.isEmpty()) {
            result.append(buildFootnoteGroup());
        }

        // 3. Disponibilidad de Datos
        if (dataAvailabilityText != null) {
            result.append(buildDataAvailability());
        }

        // 4. Referencias Bibliográficas
        if (!references.isEmpty()) {
            result.append(buildReferenceList());
        }

        result.append("</back>\n");
        return result.toString();
    }

    // ---------------------------------------------------------------
    // Métodos Privados: Construcción de Subelementos
    // ---------------------------------------------------------------

    /**
     * Construye el nodo {@code <ack>} con agradecimientos.
     */
    private String buildAcknowledgements() {
        StringBuilder sb = new StringBuilder();
        sb.append("<ack>\n");
        sb.append("<title>Agradecimientos</title>\n");
        sb.append("<p>").append(XmlUtils.escape(acknowledgementsText)).append("</p>\n");
        sb.append("</ack>\n");
        return sb.toString();
    }

    /**
     * Construye el nodo {@code <fn-group>} agrupando todas las notas
     * clasificadas por tipo.
     */
    private String buildFootnoteGroup() {
        StringBuilder sb = new StringBuilder();
        sb.append("<fn-group>\n");

        for (FootnoteEntry fn : footnotes.values()) {
            sb.append("<fn fn-type=\"").append(fn.fnType()).append("\" id=\"").append(fn.id()).append("\">\n");
            if (fn.label() != null && !fn.label().isBlank()) {
                sb.append("<label>").append(XmlUtils.escape(fn.label())).append("</label>\n");
            }
            sb.append("<p>").append(XmlUtils.escape(fn.content())).append("</p>\n");
            sb.append("</fn>\n");
        }

        sb.append("</fn-group>\n");
        return sb.toString();
    }

    /**
     * Construye la sección {@code <sec sec-type="data-availability">}.
     */
    private String buildDataAvailability() {
        StringBuilder sb = new StringBuilder();
        sb.append("<sec sec-type=\"data-availability\">\n");
        sb.append("<title>Disponibilidad de datos</title>\n");
        sb.append("<p>").append(XmlUtils.escape(dataAvailabilityText)).append("</p>\n");
        sb.append("</sec>\n");
        return sb.toString();
    }

    /**
     * Construye el nodo {@code <ref-list>} con todas las referencias.
     */
    private String buildReferenceList() {
        StringBuilder sb = new StringBuilder();
        sb.append("<ref-list>\n");
        sb.append("<title>Referencias bibliográficas</title>\n");

        for (Reference ref : references) {
            sb.append(buildReference(ref));
        }

        sb.append("</ref-list>\n");
        return sb.toString();
    }

    /**
     * Construye un nodo {@code <ref>} completo con {@code <mixed-citation>}
     * y {@code <element-citation>} estructurado.
     */
    private String buildReference(Reference ref) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ref id=\"B").append(ref.refId()).append("\">\n");
        sb.append("<label>").append(ref.label()).append("</label>\n");

        // Mixed Citation: texto plano sin estructura
        sb.append("<mixed-citation>").append(XmlUtils.escape(ref.citationText())).append("</mixed-citation>\n");

        // Element Citation: estructura granular según tipo de publicación
        sb.append(buildElementCitation(ref));

        sb.append("</ref>\n");
        return sb.toString();
    }

    /**
     * Construye el nodo {@code <element-citation>} con atributo publication-type
     * y subelementos estructurados según el tipo detectado.
     */
    private String buildElementCitation(Reference ref) {
        StringBuilder sb = new StringBuilder();
        String pubType = ref.publicationType() != null ? ref.publicationType() : "book";
        
        sb.append("<element-citation publication-type=\"").append(pubType).append("\">\n");

        // Autores / Colaboradores
        if (!ref.authors().isEmpty() || !ref.collaborators().isEmpty()) {
            sb.append(buildPersonGroup(ref.authors(), ref.collaborators()));
        }

        // Título del artículo (revistas)
        if (ref.articleTitle() != null && !ref.articleTitle().isBlank()) {
            sb.append("<article-title>").append(XmlUtils.escape(ref.articleTitle())).append("</article-title>\n");
        }

        // Fuente / Revista / Libro
        if (ref.source() != null && !ref.source().isBlank()) {
            sb.append("<source>").append(XmlUtils.escape(ref.source())).append("</source>\n");
        }

        // Año de publicación
        if (ref.year() != null && !ref.year().isBlank()) {
            sb.append("<year>").append(ref.year()).append("</year>\n");
        }

        // Volumen
        if (ref.volume() != null && !ref.volume().isBlank()) {
            sb.append("<volume>").append(ref.volume()).append("</volume>\n");
        }

        // Número/Edición
        if (ref.issue() != null && !ref.issue().isBlank()) {
            sb.append("<issue>").append(ref.issue()).append("</issue>\n");
        }

        // Páginas (fpage/lpage o elocation-id)
        if (ref.fpage() != null && !ref.fpage().isBlank()) {
            sb.append("<fpage>").append(ref.fpage()).append("</fpage>\n");
        }
        if (ref.lpage() != null && !ref.lpage().isBlank()) {
            sb.append("<lpage>").append(ref.lpage()).append("</lpage>\n");
        }
        if (ref.elocationId() != null && !ref.elocationId().isBlank()) {
            sb.append("<elocation-id>").append(ref.elocationId()).append("</elocation-id>\n");
        }

        // Lugar de publicación (libros)
        if (ref.publisherLoc() != null && !ref.publisherLoc().isBlank()) {
            sb.append("<publisher-loc>").append(XmlUtils.escape(ref.publisherLoc())).append("</publisher-loc>\n");
        }

        // Editorial (libros/tesis)
        if (ref.publisherName() != null && !ref.publisherName().isBlank()) {
            sb.append("<publisher-name>").append(XmlUtils.escape(ref.publisherName())).append("</publisher-name>\n");
        }

        // DOI
        if (ref.doi() != null && !ref.doi().isBlank()) {
            sb.append("<pub-id pub-id-type=\"doi\">").append(XmlUtils.escape(ref.doi())).append("</pub-id>\n");
        }

        // ISBN
        if (ref.isbn() != null && !ref.isbn().isBlank()) {
            sb.append("<pub-id pub-id-type=\"isbn\">").append(XmlUtils.escape(ref.isbn())).append("</pub-id>\n");
        }

        // URL / Enlace externo
        if (ref.url() != null && !ref.url().isBlank()) {
            sb.append("<ext-link ext-link-type=\"uri\" xlink:href=\"").append(XmlUtils.escape(ref.url())).append("\">");
            sb.append(XmlUtils.escape(ref.url())).append("</ext-link>\n");
        }

        // Fecha de acceso (para sitios web)
        if (ref.accessDate() != null && !ref.accessDate().isBlank()) {
            sb.append("<date-in-citation content-type=\"access-date\">").append(XmlUtils.escape(ref.accessDate())).append("</date-in-citation>\n");
        }

        sb.append("</element-citation>\n");
        return sb.toString();
    }

    /**
     * Construye el nodo {@code <person-group>} con autores individuales
     * y/o colaboradores (organizaciones).
     */
    private String buildPersonGroup(List<Author> authors, List<Collaborator> collaborators) {
        StringBuilder sb = new StringBuilder();
        sb.append("<person-group person-group-type=\"author\">\n");

        for (Author auth : authors) {
            sb.append("<name>\n");
            sb.append("<surname>").append(XmlUtils.escape(auth.surname())).append("</surname>\n");
            sb.append("<given-names>").append(XmlUtils.escape(auth.givenNames())).append("</given-names>\n");
            sb.append("</name>\n");
        }

        for (Collaborator collab : collaborators) {
            sb.append("<collab>").append(XmlUtils.escape(collab.name())).append("</collab>\n");
        }

        sb.append("</person-group>\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Métodos Privados: Parseo de Referencias y Metadatos
    // ---------------------------------------------------------------

    /**
     * Parsea una cita textual completa e intenta identificar sus componentes
     * estructurados mediante patrones de expresiones regulares y heurísticas.
     * Retorna un objeto {@code Reference} con todos los metadatos extraídos.
     */
    private Reference parseReference(String refId, String citationText) {
        String label = refId;
        String publicationType = "book"; // tipo por defecto
        List<Author> authors = new ArrayList<>();
        List<Collaborator> collaborators = new ArrayList<>();
        String articleTitle = null;
        String source = null;
        String year = null;
        String volume = null;
        String issue = null;
        String fpage = null;
        String lpage = null;
        String elocationId = null;
        String doi = null;
        String isbn = null;
        String url = null;
        String publisherName = null;
        String publisherLoc = null;
        String accessDate = null;

        // Extraer DOI
        Matcher doiMatcher = DOI_PATTERN.matcher(citationText);
        if (doiMatcher.find()) {
            doi = doiMatcher.group(1);
        }

        // Extraer URL
        Matcher urlMatcher = URL_PATTERN.matcher(citationText);
        if (urlMatcher.find()) {
            url = urlMatcher.group();
        }

        // Extraer año
        Matcher yearMatcher = YEAR_PATTERN.matcher(citationText);
        if (yearMatcher.find()) {
            year = yearMatcher.group(1);
        }

        // Extraer ISBN
        Matcher isbnMatcher = ISBN_PATTERN.matcher(citationText);
        if (isbnMatcher.find()) {
            isbn = isbnMatcher.group();
        }

        // Intentar parsear como artículo de revista
        Matcher journalMatcher = JOURNAL_PATTERN.matcher(citationText);
        if (journalMatcher.matches()) {
            publicationType = "journal";
            String authorsStr = journalMatcher.group(1);
            articleTitle = journalMatcher.group(2);
            source = journalMatcher.group(3);
            year = journalMatcher.group(4);
            volume = journalMatcher.group(5);
            issue = journalMatcher.group(6);
            String pages = journalMatcher.group(7);
            
            parseAuthors(authorsStr, authors, collaborators);
            
            // Procesar páginas: pueden ser "111-120" o "e0027"
            if (pages != null && !pages.isBlank()) {
                if (pages.startsWith("e")) {
                    elocationId = pages;
                } else {
                    String[] pageRange = pages.split("-");
                    fpage = pageRange[0];
                    if (pageRange.length > 1) {
                        lpage = pageRange[1];
                    }
                }
            }
            
            return new Reference(refId, label, citationText, publicationType, authors, collaborators,
                    articleTitle, source, year, volume, issue, fpage, lpage, elocationId, 
                    doi, isbn, url, publisherName, publisherLoc, accessDate);
        }

        // Intentar parsear como libro
        Matcher bookMatcher = BOOK_PATTERN.matcher(citationText);
        if (bookMatcher.matches()) {
            publicationType = "book";
            String authorsStr = bookMatcher.group(1);
            source = bookMatcher.group(2); // Título del libro
            publisherName = bookMatcher.group(3); // Editorial
            year = bookMatcher.group(4);
            
            parseAuthors(authorsStr, authors, collaborators);
            
            // Intentar extraer lugar de publicación
            extractPublisherLocation(publisherName, citationText);
            
            return new Reference(refId, label, citationText, publicationType, authors, collaborators,
                    articleTitle, source, year, volume, issue, fpage, lpage, elocationId, 
                    doi, isbn, url, publisherName, publisherLoc, accessDate);
        }

        // Intentar parsear como tesis
        Matcher thesisMatcher = THESIS_PATTERN.matcher(citationText);
        if (thesisMatcher.matches()) {
            publicationType = "thesis";
            String authorsStr = thesisMatcher.group(1);
            source = thesisMatcher.group(2);
            publisherName = thesisMatcher.group(4);
            year = thesisMatcher.group(5);
            
            parseAuthors(authorsStr, authors, collaborators);
            
            return new Reference(refId, label, citationText, publicationType, authors, collaborators,
                    articleTitle, source, year, volume, issue, fpage, lpage, elocationId, 
                    doi, isbn, url, publisherName, publisherLoc, accessDate);
        }

        // Si contiene [Internet] o URL, probablemente sea una página web
        if (citationText.toLowerCase().contains("[internet]") || url != null) {
            publicationType = "webpage";
            
            // Extraer los primeros términos como posible autor
            int firstDot = citationText.indexOf('.');
            if (firstDot > 0) {
                String authorPart = citationText.substring(0, firstDot);
                parseAuthors(authorPart, authors, collaborators);
            }
            
            // El título/fuente es el texto completo
            source = citationText;
        } else {
            // Por defecto, tratar como libro o misc
            int firstDot = citationText.indexOf('.');
            if (firstDot > 0) {
                String authorPart = citationText.substring(0, firstDot);
                parseAuthors(authorPart, authors, collaborators);
            }
        }

        return new Reference(refId, label, citationText, publicationType, authors, collaborators,
                articleTitle, source, year, volume, issue, fpage, lpage, elocationId, 
                doi, isbn, url, publisherName, publisherLoc, accessDate);
    }

    /**
     * Parsea una cadena de autores y separa autores individuales
     * de colaboradores (organizaciones).
     */
    private void parseAuthors(String authorsStr, List<Author> authors, List<Collaborator> collaborators) {
        if (authorsStr == null || authorsStr.isBlank()) return;

        // Detectar si es una organización
        if (isOrganization(authorsStr)) {
            collaborators.add(new Collaborator(authorsStr.trim()));
            return;
        }

        // Dividir por separadores típicos (and, y, et al., etc)
        String[] parts = authorsStr.split("(?:,\\s*(?:and|y|et)?\\s*|\\s+and\\s+|\\s+y\\s+)");
        
        for (String part : parts) {
            String author = part.trim();
            
            if (author.equalsIgnoreCase("et al.") || author.equalsIgnoreCase("et al")) {
                // Ignorar "et al" en parsing de autores individuales
                continue;
            }
            
            if (isOrganization(author)) {
                collaborators.add(new Collaborator(author));
            } else {
                Author parsed = parseAuthorName(author);
                if (parsed != null) {
                    authors.add(parsed);
                }
            }
        }
    }

    /**
     * Parsea un nombre individual de autor extrayendo apellido y nombres.
     * Usa heurísticas básicas: asumir que la última palabra es el apellido.
     */
    private Author parseAuthorName(String name) {
        if (name == null || name.isBlank()) return null;

        name = name.trim();
        
        // Formato típico: "Apellido Nombres" o "Nombres Apellido"
        // Heurística: si hay mayúscula, considerar la primera mayúscula seguida
        // como inicio del apellido
        
        int lastSpace = name.lastIndexOf(' ');
        if (lastSpace <= 0) {
            // Un solo término: asumir como apellido
            return new Author(name, "");
        }

        String surname = name.substring(lastSpace + 1).trim();
        String givenNames = name.substring(0, lastSpace).trim();

        return new Author(surname, givenNames);
    }

    /**
     * Detecta si una cadena representa una organización en lugar de un individuo.
     */
    private boolean isOrganization(String text) {
        if (text == null) return false;
        
        text = text.toLowerCase();
        
        // Palabras clave típicas de organizaciones
        return text.contains("organization") || text.contains("organización") ||
               text.contains("ministry") || text.contains("ministerio") ||
               text.contains("university") || text.contains("universidad") ||
               text.contains("institute") || text.contains("instituto") ||
               text.contains("association") || text.contains("asociación") ||
               text.contains("department") || text.contains("departamento") ||
               text.contains("march of dimes") || text.contains("who") ||
               text.contains("world health") || text.contains("fund") ||
               text.contains("fondo") || (!text.contains(",") && text.length() > 30);
    }

    /**
     * Intenta extraer el lugar de publicación desde una cadena de editor.
     * (Implementación simplificada.)
     */
    private void extractPublisherLocation(String publisherInfo, String fullCitation) {
        // Buscar patrones como "Ciudad: Editorial" o "Editorial, Ciudad"
        if (publisherInfo == null) return;
        
        int colonIdx = publisherInfo.indexOf(':');
        if (colonIdx > 0) {
            // Posible formato "Lugar: Editorial"
            String potential = publisherInfo.substring(0, colonIdx).trim();
            if (potential.length() < 30) { // Asumir que es un lugar si es corto
                publisherLoc = potential;
            }
        }
    }

    /**
     * Normaliza el tipo de nota al pie a uno de los tipos válidos de SciELO PS.
     */
    private String normalizeFootnoteType(String fnType) {
        if (fnType == null) return FootnoteType.OTHER.type;
        
        String lower = fnType.toLowerCase();
        
        if (lower.contains("financial") || lower.contains("funding") || lower.contains("financing")) {
            return FootnoteType.FINANCIAL_DISCLOSURE.type;
        }
        if (lower.contains("conflict")) {
            return FootnoteType.CONFLICT.type;
        }
        if (lower.contains("contribution") || lower.contains("contribución")) {
            return FootnoteType.CONTRIBUTIONS.type;
        }
        if (lower.contains("equal")) {
            return FootnoteType.EQUAL.type;
        }
        
        return FootnoteType.OTHER.type;
    }
}
