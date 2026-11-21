# Documentación Técnica del Proyecto: Conversor DOCX → JATS XML (SciELO Publishing Schema)

**Institución:** Universidad Nacional de Lanús (UNLa) — Licenciatura en Sistemas  
**Marco Académico:** Prácticas Profesionalizantes (PPP)  
**Revista de Aplicación:** *Salud Colectiva* (ISSN 1851-8265 / 1414-9089)  
**Estándares Implementados:** JATS XML 1.1 - 1.3 (NISO Z39.96) / SciELO Publishing Schema (SPS 1.9)  

---

## 1. Visión General del Proyecto

El proyecto tiene como propósito resolver y automatizar la transformación de manuscritos científicos presentados en formato Microsoft Word (`.docx`) al formato estándar internacional **JATS XML** conforme a los lineamientos estrictos de **SciELO Publishing Schema (SPS)** adoptados por la revista académica *Salud Colectiva* de la Universidad Nacional de Lanús.

El sistema se compone de una arquitectura desacoplada:
1. **Backend en Spring Boot (Java 17+)**: Motor de parseo heurístico basado en [Apache POI](backend/pom.xml), procesamiento del DOM XML, extracción estructurada de metadatos, generación modular de secciones (`<front>`, `<body>`, `<back>`), validación sintáctica contra el estándar y endpoints REST.
2. **Frontend en React + Vite**: Interfaz de usuario con identidad visual editorial universitaria (UNLa), carga interactiva por arrastre (drag & drop), previsualización con resaltado de sintaxis, editor reactivo de metadatos del `<front>` con inputs adaptables y exportación de archivos XML o paquetes comprimidos ZIP con imágenes.

```mermaid
flowchart TD
    A[Documento .docx] --> B[ConvertController / DocxToJatsConverter]
    B --> C[DocxFrontParser]
    B --> D[DocxBodyParser]
    B --> E[DocxBackParser]
    
    C --> F[JatsFrontBuilder]
    D --> G[JatsBodyBuilder]
    E --> H[JatsBackBuilder]
    
    D -.-> I[ImageRegistry & RunRenderer]
    E -.-> I
    
    F --> J[Ensamble Article XML]
    G --> J
    H --> J
    
    J --> K[JatsValidator]
    K --> L[API REST / Previsualización]
    
    L --> M[Frontend React UI]
    M --> N[Formulario de Edición Rápida]
    N --> O[FrontEditController / JatsFrontEditor]
    O --> P[XML Actualizado & Descarga]
```

---

## 2. Arquitectura de Módulos del Backend

El código del backend en [backend/src/main/java/com/converter/docxjats/](backend/src/main/java/com/converter/docxjats/) fue completamente modularizado para garantizar la separación de responsabilidades:

### 2.1 Orquestador Central: [DocxToJatsConverter.java](backend/src/main/java/com/converter/docxjats/service/DocxToJatsConverter.java)
- Coordina el flujo de conversión de documentos.
- Extrae el archivo a un almacenamiento temporal seguro (`Files.createTempFile`).
- Ejecuta secuencialmente los tres parsers ([DocxFrontParser](backend/src/main/java/com/converter/docxjats/service/DocxFrontParser.java), [DocxBodyParser](backend/src/main/java/com/converter/docxjats/service/DocxBodyParser.java), [DocxBackParser](backend/src/main/java/com/converter/docxjats/service/DocxBackParser.java)) delimitando los rangos de párrafos exactos mediante cursores (`bodyStart`, `bodyEnd`).
- Realiza el ensamblado final del documento con los encabezados DTD oficiales:
  ```xml
  <!DOCTYPE article PUBLIC "-//NLM//DTD JATS (Z39.96) Journal Publishing DTD v1.1 20151215//EN" 
    "https://jats.nlm.nih.gov/publishing/1.1/JATS-journalpublishing1.dtd">
  <article article-type="research-article" dtd-version="1.1" specific-use="sps-1.9" xml:lang="es" ...>
  ```

---

### 2.2 Procesamiento de Metadatos: Sección `<front>`

Módulos involucrados:
- [DocxFrontParser.java](backend/src/main/java/com/converter/docxjats/service/DocxFrontParser.java)
- [JatsFrontBuilder.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsFrontBuilder.java)

#### Funcionalidades implementadas:
1. **Metadatos de Revista y Artículo (`<journal-meta>`, `<article-meta>`):**
   - Detección de identificadores de publicación: DOI (`10.18294/...`), ID editorial (`publisher-id`), volumen, `elocation-id` y número.
   - Parametrización de versión SPS (`specific-use="sps-1.9"`), ISSNs (`ppub` y `epub`), nombre y abreviatura de la revista y editorial (*Universidad Nacional de Lanús*).
2. **Títulos y Traducciones (`<title-group>`):**
   - Captura del título principal (`<article-title>`), subtítulo (`<subtitle>`) y título traducido en inglés (`<trans-title-group xml:lang="en"><trans-title>`), preservando el formato inline (negritas, cursivas).
3. **Autores y Afiliaciones (`<contrib-group>`, `<aff>`):**
   - Extracción de nombres (`<surname>`, `<given-names>`) y detección automática de identificadores persistentes **ORCID** (mediante hipervínculos `orcid.org` o regex de 16 dígitos).
   - Vinculación cruzada mediante superíndices (`<xref ref-type="aff" rid="affX">`).
   - Normalización de afiliaciones institucionales jerárquicas conforme a SciELO:
     - Institución principal (`<institution content-type="orgname">`).
     - Divisiones y unidades académicas (`<institution content-type="orgdiv1">`, `<institution content-type="orgdiv2">`).
     - Cadena completa sin procesar (`<institution content-type="original">`).
     - Localización geográfica: ciudad (`<city>`), provincia/estado (`<state>`) y país con código ISO (`<country country="AR">`).
     - Detección de correos electrónicos (`<email>`).
4. **Historial Editorial (`<history>`):**
   - Reconocimiento de fechas clave del ciclo de vida editorial (`received`, `rev-recd`, `accepted`) en español, inglés y portugués.
   - Conversión de meses textuales a formato normalizado `<day>`, `<month>`, `<year>`.
5. **Resúmenes y Palabras Clave (`<abstract>`, `<kwd-group>`):**
   - Identificación de secciones de resumen en español (`xml:lang="es"`) y en inglés (`xml:lang="en"`).
   - Extracción de palabras clave individuales (`<kwd>`) agrupadas por idioma.
6. **Financiamiento y Notas Editoriales:**
   - Detección de financiamiento (`<funding-group>`, `<award-group>`, `<award-id>`).
   - Notas de autor (`<author-notes>`): declaraciones de conflicto de intereses, contribución autoral (`<fn fn-type="con">`) y correspondencia.

---

### 2.3 Procesamiento del Cuerpo del Texto: Sección `<body>`

Módulos involucrados:
- [DocxBodyParser.java](backend/src/main/java/com/converter/docxjats/service/DocxBodyParser.java)
- [JatsBodyBuilder.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsBodyBuilder.java)
- [RunRenderer.java](backend/src/main/java/com/converter/docxjats/service/jats/RunRenderer.java)
- [ImageRegistry.java](backend/src/main/java/com/converter/docxjats/service/jats/ImageRegistry.java)

#### Funcionalidades implementadas:
1. **Estructura de Secciones Anidadas (`<sec>`):**
   - Detección automática de estilos de Word (`Heading 1..6` / `Título 1..6`).
   - Apertura y cierre jerárquico de etiquetas `<sec id="secX">` con títulos asociados (`<title>`).
   - Asignación inteligente del tipo de sección (`sec-type="intro|methods|results|discussion|conclusions"`).
2. **Formato Tipográfico Inline ([RunRenderer.java](backend/src/main/java/com/converter/docxjats/service/jats/RunRenderer.java)):**
   - Convierte estilos de Apache POI a etiquetas XML: `<bold>`, `<italic>`, `<underline>`, `<sup>`, `<sub>`.
   - Conversión de llamadas bibliográficas numéricas superíndices a referencias cruzadas `<xref ref-type="bibr" rid="BX">`.
   - Limpieza y sanitización de caracteres especiales XML con [XmlUtils.java](backend/src/main/java/com/converter/docxjats/service/jats/XmlUtils.java).
3. **Tablas Estructuradas:**
   - Convierte tablas Word (`XWPFTable`) a la estructura estándar `<table-wrap><table><thead><tbody>`.
   - Preservación de saltos y estilos en celdas individuales eliminando atributos CSS inline innecesarios.
4. **Figuras e Imágenes:**
   - Identificación de párrafos con imágenes (`XWPFPicture`).
   - Almacenamiento en memoria en [ImageRegistry.java](backend/src/main/java/com/converter/docxjats/service/jats/ImageRegistry.java) y referenciación en el XML como `<fig id="figX"><graphic xlink:href="images/imgN.ext"/></fig>`.
   - Soporte para gráficos en línea (`<inline-graphic>`).

---

### 2.4 Procesamiento del Cierre del Documento: Sección `<back>`

Módulos involucrados:
- [DocxBackParser.java](backend/src/main/java/com/converter/docxjats/service/DocxBackParser.java)
- [JatsBackBuilder.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsBackBuilder.java)
- Guía de diseño: [JATS_BACK_BUILDER_GUIDE.md](JATS_BACK_BUILDER_GUIDE.md)

#### Funcionalidades implementadas:
1. **Agradecimientos (`<ack>`):**
   - Captura del encabezado y contenido de agradecimientos a revisores o equipos colaboradores.
2. **Notas al Pie Clasificadas (`<fn-group>`):**
   - Soporte para `financial-disclosure`, `conflict`, `con` (contribución autoral), `equal` y `other`.
3. **Disponibilidad de Datos (`<sec sec-type="data-availability">`):**
   - Detección de la declaración de disponibilidad de bases de datos de la investigación.
4. **Lista de Referencias Bibliográficas Estructuradas (`<ref-list>`):**
   - Detección automática del tipo de publicación:
     - **Artículos de Revistas (`publication-type="journal"`):** extrae autores, título de artículo, nombre de revista, año, volumen, número, páginas y DOI.
     - **Libros (`publication-type="book"`):** extrae autores, título, editorial, ciudad y año.
     - **Tesis (`publication-type="thesis"`):** tipo de tesis, universidad, año.
     - **Sitios Web (`publication-type="webpage"`):** URLs, fechas de consulta (`[citado ...]`).
   - Generación dual requerida por SciELO:
     - `<mixed-citation>`: texto plano completo de la referencia.
     - `<element-citation>`: descomposición granular de cada metadato.

---

### 2.5 Validación Sintáctica y Servicio de Patrones

Módulos involucrados:
- [JatsValidator.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsValidator.java)
- [JatsPatternService.java](backend/src/main/java/com/converter/docxjats/service/JatsPatternService.java)
- [archivosXMLPatron/](archivosXMLPatron/)

#### Funcionalidades implementadas:
1. **Validación XML basada en DOM:**
   - Inspecciona que el elemento raíz sea `<article>`.
   - Verifica la presencia obligatoria de `<front>` (con `<journal-meta>` y `<article-meta>`), `<body>` y `<back>`.
   - Retorna una estructura [ValidationResult](backend/src/main/java/com/converter/docxjats/service/jats/JatsValidator.java) con la lista de errores encontrados.
2. **Homologación con Artículos Patrón Certificados:**
   - Integración con los artículos certificados de la revista *Salud Colectiva*:
     - **Artículo 5939:** *Prácticas de fin de vida y directivas anticipadas de voluntad* ([1851-8265-scol-22-e5939.xml](archivosXMLPatron/1851-8265-scol-22-e5939.xml) / [ID-5939](ID-5939_Eich%20et%20al_XML-es%20(2).docx)).
     - **Artículo 6032:** *Cesáreas como expresión biopolítica del nacimiento* ([1851-8265-scol-22-e6032.xml](archivosXMLPatron/1851-8265-scol-22-e6032.xml) / [ID-6032](ID-6032_Perner+et+al_Word-XML.docx)).
   - Pruebas automatizadas en [JatsControllerTest.java](backend/src/test/java/com/converter/docxjats/controller/JatsControllerTest.java) con Spring Boot MockMvc.

---

### 2.6 Edición Rápida del `<front>` (In-Place)

Módulos involucrados:
- [JatsFrontEditor.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsFrontEditor.java)
- [FrontEditController.java](backend/src/main/java/com/converter/docxjats/controller/FrontEditController.java)
- [FrontFields.java](backend/src/main/java/com/converter/docxjats/dto/FrontFields.java)

#### Funcionalidades implementadas:
- Permite leer y reescribir metadatos del `<front>` directamente sobre el XML ya generado **sin requerir volver a cargar el .docx**:
  - `POST /api/convert/front-fields`: parsea el XML actual y devuelve en JSON los campos del formulario.
  - `POST /api/convert/apply-front`: aplica las ediciones sobre el DOM XML y devuelve el nuevo XML revalidado.
  - `POST /api/convert/export-xml`: descarga el XML editado directamente como archivo `.xml`.

---

## 3. Desarrollo en el Frontend (React + Vite)

El frontend en [frontend/src/](frontend/src/) fue diseñado con una estética editorial universitaria cuidada:

1. **Diseño e Identidad Institucional:**
   - Tipografía refinada: *Source Serif 4* para encabezados editoriales, *Inter* para elementos de UI y *JetBrains Mono* para código XML.
   - Paleta institucional UNLa (color borravino/granate `#800000`, fondo papel `#f4f3ef`, acentos `#1c2430`).
   - Incorporación del logo oficial de la Universidad Nacional de Lanús ([unla-logo.png](frontend/src/assets/unla-logo.png)).
2. **Componente de Entrada Adaptable (`AutoInput`):**
   - Inputs inteligentes en [App.jsx](frontend/src/App.jsx) que calculan dinámicamente su anchura en unidades de caracteres (`ch`), evitando formularios estáticos desalineados.
3. **Formulario de Edición Integral de Metadatos:**
   - **Datos de Revista:** Título, editorial, ISSN impreso y electrónico.
   - **Identificadores y Fechas:** DOI, ID editorial, día, mes y año de publicación.
   - **Títulos y Resúmenes:** Título original, subtítulo, traducción al inglés, resumen en español y abstract en inglés.
   - **Palabras Clave:** Edición de listas de términos en español e inglés.
   - **Permisos y Licencia:** Declaración de copyright y URL de la licencia Creative Commons.
   - **Gestión Dinámica de Autores:** Incorporación, modificación o remoción de autores, especificando nombre, apellido, ORCID, correo electrónico y filiación institucional.
4. **Auto-completado y Extracción de Correos y Afiliaciones:**
   - Función cliente `parseAuthorsFromXml` en [App.jsx](frontend/src/App.jsx) que lee elementos `<contrib>`, resuelve las referencias `<xref rid="...">` contra los nodos `<aff>` e identifica correos asociados automáticamente.
5. **Visor XML Integrado:**
   - Previsualización interactiva con coloreado sintáctico de etiquetas XML en el bloque de salida.

---

## 4. Matriz de Endpoints de la API REST

| Método | Endpoint | Parámetros / Body | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/convert/preview` | Multipart (`file`: `.docx`) | Convierte el archivo y devuelve el XML plano junto con la cantidad de imágenes detectadas. |
| `POST` | `/api/convert/xml` | Multipart (`file`: `.docx`) | Descarga directa del archivo XML generado (`<nombre>-jats.xml`). |
| `POST` | `/api/convert/package` | Multipart (`file`: `.docx`) | Descarga un `.zip` conteniendo `article.xml` y la subcarpeta `images/` con los binarios de las imágenes. |
| `POST` | `/api/convert/front-fields` | JSON: `{"xml": "..."}` | Extrae los campos editables del `<front>` desde el XML para precargar el formulario. |
| `POST` | `/api/convert/apply-front` | JSON: `{"xml": "...", "front": {...}}` | Aplica los campos editados en el XML y ejecuta la validación DOM. |
| `POST` | `/api/convert/export-xml` | JSON: `{"xml": "...", "filename": "..."}` | Descarga el XML editado como archivo adjunto `.xml`. |
| `GET / POST` | `/jats/{articleId}` | Path o JSON: `articleId` (`5939`, `6032`) | Recupera y valida el documento XML patrón correspondiente de la revista *Salud Colectiva*. |

---

## 5. Historial de Hitos y Correcciones Aplicadas

De acuerdo al registro de control de versiones y diagnóstico de la práctica:

| Área | Problema Inicial | Solución Implementada |
| :--- | :--- | :--- |
| **Arquitectura** | Monolito de parseo difícil de mantener | Separación modular en `DocxFrontParser`, `DocxBodyParser`, `DocxBackParser` y sus respectivos Builders. |
| **Metadatos SciELO** | Fuga de metadatos del encabezado al cuerpo del documento (`<body>`) | Delimitación estricta de índices de párrafos (`bodyStart` / `bodyEnd`) calculados tras consumir el front. |
| **Geografía de Afiliaciones** | Excepciones o falta de atributos ante ciudades/estados con formatos variados | Regex `LOCATION_COUNTRY_TAIL` con soporte bidireccional para 2 partes (*Rosario, Argentina*) y 3 partes (*Campinas, SP, Brasil*). |
| **Instituciones** | Texto plano sin separación jerárquica | Normalización en `<institution content-type="orgname">`, `orgdiv1` y `orgdiv2` sin caracteres de puntuación residuales. |
| **Fechas de Historial** | Errores por formatos de mes con/sin preposición "de" o numéricos | Mapeo léxico multi-idioma (español, portugués, inglés) extrayendo componentes hacia formato estándar `<date>`. |
| **Referencias (`<back>`)** | Referencias truncadas y texto sin estructurar | Parser heurístico con expresiones regulares por tipología (journal, book, thesis, webpage) y generación dual `<mixed-citation>` + `<element-citation>`. |
| **Tablas y Estilos** | Inyección de estilos CSS inline que rompían el renderizador de SciELO | Remoción de CSS inline y delegación del diseño a hojas XSLT/CSS del visor editorial. |
| **UI & Experiencia** | Formulario estático y pérdida de correos al editar autores | Extracción cliente-servidor de correos y afiliaciones, inputs elásticos (`AutoInput`) y previsualización en tiempo real. |

---

## 6. Caminos Desechados y Decisiones Técnicas Críticas (Lecciones Aprendidas)

Uno de los requerimientos metodológicos esenciales de la Práctica Profesionalizante y del posterior Trabajo Final de Integración (TFI) consiste en registrar **no solo el código funcional final, sino también las hipótesis refutadas, los enfoques descartados y los obstáculos superados**. Documentar las vías que no condujeron a buen puerto previene regresiones y fundamenta la madurez de la solución técnica:

### 6.1 Descarte del "Peso del Archivo" como Criterio de Verificación
- **Hipótesis inicial:** En etapas tempranas de experimentación, se asumió que si el tamaño en bytes del XML generado era equivalente al del archivo XML patrón de referencia de la cursada, la conversión era correcta.
- **Razón del descarte:** En la especificación XML JATS, el tamaño en disco es una métrica engañosa y espuria. Un documento con relleno de espacios o texto duplicado puede tener un peso idéntico a un archivo patrón, pero fallar críticamente por una etiqueta mal anidada (ej.: un `<italic>` dentro de un nodo no admitido o un `<contrib>` huérfano). La conformidad editorial exige **precisión semántica estricta en el árbol DOM y cumplimiento del DTD/XSD**, independientemente del peso en bytes.
- **Solución adoptada:** Sustituir la comparación de tamaño por **validación estructural de esquemas y evaluación mediante Stylecheckers formales** (SciELO y PubMed Central).

### 6.2 Descarte del Parseo Monolítico con Expresiones Regulares sobre Texto Plano
- **Hipótesis inicial:** Extraer todo el texto del `.docx` a un String continuo mediante Apache POI y aplicar expresiones regulares complejas para detectar autores, títulos y secciones.
- **Razón del descarte:** Provocaba pérdida irremediable del contexto estructural:
  1. No era posible asociar superíndices de llamadas de notas al pie o afiliaciones con su autor correspondiente.
  2. Los estilos de Word (negrita, cursiva, enlaces) quedaban desfasados o se perdían al convertir a texto plano.
  3. Las tablas e imágenes incrustadas no encajaban en un flujo textual lineal sin romper la secuencia de párrafos.
- **Solución adoptada:** Procesar directamente el modelo de objetos de POI (`XWPFDocument`, `XWPFParagraph`, `XWPFRun`, `XWPFTable`) conservando los atributos XML subyacentes (`w:rPr`, `w:vertAlign`) en un pipeline de tres etapas independientes (`Front`, `Body`, `Back`).

### 6.3 Descarte de Estilos CSS Inline en Tablas
- **Hipótesis inicial:** Conservar los anchos de columna en píxeles, colores de fondo (`background-color`) y bordes que los autores configuran en Microsoft Word e inyectarlos como atributos `style="..."` dentro de `<table>`, `<tr>` y `<td>`.
- **Razón del descarte:** Los visores y stylesheets XSLT oficiales de SciELO (como *SciELO Publishing Framework* o visores PMC) rechazan o desfiguran el marcado cuando detectan estilos visuales inline ad-hoc. SciELO impone el principio de **separación estricta entre semántica de datos y presentación visual**.
- **Solución adoptada:** Limpiar todo el CSS inline y generar exclusivamente marcado estructural semántico (`<table-wrap>`, `<table>`, `<thead>`, `<tbody>`, `<tr>`, `<th>`, `<td>`), permitiendo que el diseño tipográfico y responsive sea gestionado por el motor de publicación editorial o el visor OJS.

### 6.4 Descarte del Almacenamiento de Imágenes en Base64 Embebido
- **Hipótesis inicial:** Incrustar los binarios de las imágenes directamente dentro del XML como cadenas Base64 (`<graphic>data:image/png;base64,...</graphic>`).
- **Razón del descarte:** JATS y SciELO prohíben la serialización en Base64 para figuras del artículo. El estándar exige referencias relativas a archivos externos (`<graphic xlink:href="images/img1.png"/>`) para posibilitar la optimización independiente, indexación en repositorios y generación de miniaturas.
- **Solución adoptada:** Creación del servicio [ImageRegistry.java](backend/src/main/java/com/converter/docxjats/service/jats/ImageRegistry.java) y empaquetado final en archivo ZIP (`POST /api/convert/package`) con estructura estándar: `article.xml` en la raíz y carpeta `images/` con los archivos originales extraídos del DOCX.

### 6.5 Descarte del Reprocesamiento Completo de DOCX para Ajustes Menores de Metadatos
- **Hipótesis inicial:** Si un usuario detectaba un error ortográfico en el título o un ORCID faltante, debía modificar el archivo Word original y reiniciar toda la conversión.
- **Razón del descarte:** Este flujo resultaba lento, propenso a frustración editorial y generaba discrepancias entre versiones intermedias del manuscrito.
- **Solución adoptada:** Implementación de la arquitectura de edición in-place con [JatsFrontEditor.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsFrontEditor.java): el usuario edita los campos del `<front>` directamente en la interfaz web interactiva y el backend actualiza el DOM XML en memoria en milisegundos sin requerir re-conversión del archivo original.

---

## 7. Marco Teórico y de Estándares: JATS DTD y SciELO Publishing Schema (SPS)

El desarrollo del conversor se fundamenta en estándares internacionales de preservación digital y marcación de literatura científica:

### 7.1 Estándar NISO JATS (Z39.96) y Evolución de DTDs
**JATS (Journal Article Tag Suite)** es la norma ANSI/NISO Z39.96 desarrollada por la Biblioteca Nacional de Medicina de los Estados Unidos (NLM) para la estructuración semántica de manuscritos científicos.
- **JATS 1.0 (2012):** Primera estandarización formal por NISO.
- **JATS 1.1 (2015):** Versión de referencia consolidada en la producción editorial global. Introduce soporte robusto para identificadores persistentes (DOI, ORCID, FundRef), licencias de acceso abierto con metadatos ALI (`<ali:license_ref>`), y esquemas de disponibilidad de datos.
- **JATS 1.2 / 1.3 (2019-2021):** Incorporación de taxonomías estandarizadas para roles de autoría (CRediT - *Contributor Roles Taxonomy*), identificadores ROR (*Research Organization Registry*) y marcado enriquecido para repositorios pre-print y ciencia abierta.

### 7.2 SciELO Publishing Schema (SPS 1.9 vs. 1.10)
SciELO (*Scientific Electronic Library Online*) adopta JATS pero impone un conjunto de reglas, restricciones y validaciones adicionales denominado **SPS**:
- **`specific-use="sps-1.9"`:** Versión vigente ampliamente homologada en las colecciones SciELO. Exige:
  1. Estructuración dual obligatoria de referencias bibliográficas: `<mixed-citation>` (texto completo para lectura humana) y `<element-citation>` (desglose de campos para indexadores).
  2. Identificación estricta de afiliaciones institucionales normalizadas con país en norma ISO 3166-1 alfa-2.
  3. Marcado obligatorio de declaraciones de conflicto de intereses, contribución de autoría y disponibilidad de datos.
  4. Atributo `xml:lang` en elementos multilingües (títulos traducidos, resúmenes, palabras clave).
- **SPS 1.10 (Nueva especificación):** Avanza en la exigencia de trazabilidad del proceso editorial: obligatoriedad del rol de los editores asociados, soporte formal para preprints citados y ampliación de esquemas de datos abiertos conforme al movimiento de Ciencia Abierta.

### 7.3 Taxonomía del Atributo `article-type`
El elemento raíz `<article>` debe tipificar la naturaleza académica del texto conforme a la [Tag Library de la NLM](https://jats.nlm.nih.gov/nlm-dtd/articleauthoring/tag-library/2.1/n-24v0.html):
- `research-article`: Artículo de investigación original con resultados empíricos o teóricos.
- `review-article`: Artículo de revisión bibliográfica, sistemática o meta-análisis.
- `editorial`: Texto de opinión o balance escrito por el equipo editorial.
- `letter`: Carta al editor o comunicación breve sobre artículos previamente publicados.
- `case-report`: Reporte de caso clínico o institucional.
- `brief-report`: Comunicación corta o reporte breve de resultados preliminares.
- `book-review`: Reseña de un libro de reciente publicación.

---

## 8. Visión Multirevista ("I Love XML-JATS") y Arquitectura de los 4 Bloques

Pensando en la evolución de la herramienta hacia un servicio multirevista universal —un **"I Love XML-JATS"** accesible para cualquier publicación científica académica—, se identificó un principio clave: **el archivo Word del autor nunca contiene la totalidad de los metadatos de publicación final**. 

La publicación electrónica exige datos institucionales y de catalogación propios del equipo editorial. Para resolver esto de manera desacoplada, el sistema estructura la información editorial en **4 bloques fundamentales**:

```
+---------------------------------------------------------------------------------+
|                      INSTANCIA DE CONFIGURACIÓN EDITORIAL                        |
+---------------------------------------------------------------------------------+
| BLOQUE 1: Tipo de Artículo                                                      |
|   <article article-type="research-article" ...>                                 |
+---------------------------------------------------------------------------------+
| BLOQUE 2: Versiones de Estándares Raíz                                          |
|   dtd-version="1.1" specific-use="sps-1.9" xml:lang="es"                        |
+---------------------------------------------------------------------------------+
| BLOQUE 3: Datos de Revista, Editorial y Sección                                 |
|   <journal-meta>: NLM-TA, publisher-id, journal-title, issn ppub/epub,         |
|                   publisher-name                                                |
|   <article-meta>: DOI, <subj-group subj-group-type="heading"><subject>          |
+---------------------------------------------------------------------------------+
| BLOQUE 4: Fechas, Numeración, Historial y Licencias                             |
|   <pub-date> (electrónica y colección), <volume>, <elocation-id>                |
|   <history>: <date date-type="received|accepted|pub">                           |
|   <permissions>: Creative Commons (CC BY 4.0, CC BY-NC 4.0, etc.)              |
+---------------------------------------------------------------------------------+
```

Esta configuración permite parametrizar perfiles por revista, de modo que *Salud Colectiva* u otra publicación universitaria pueda precargar sus datos institucionales y procesar manuscritos en lote garantizando consistencia.

---

## 9. Protocolo de Calidad y Validación con Stylecheckers Formales

Para asegurar que los archivos XML generados por la herramienta sean aceptados directamente por los índices internacionales sin requerir remediación manual, el flujo de desarrollo se valida contra los motores oficiales de auditoría de calidad:

1. **SciELO Stylechecker Oficial:**
   - **URL del servicio:** [https://manager.scielo.org/tools/validators/stylechecker/](https://manager.scielo.org/tools/validators/stylechecker/)
   - **Función:** Analiza la conformidad con el esquema SPS vigente, validando la estructura del `<front>`, los códigos de país de las afiliaciones, las fechas del `<history>` y la consistencia de las referencias bibliográficas en `<ref-list>`.
2. **PubMed Central (PMC) Stylechecker:**
   - **URL del servicio:** [https://pmc.ncbi.nlm.nih.gov/tools/stylechecker/](https://pmc.ncbi.nlm.nih.gov/tools/stylechecker/)
   - **Función:** Valida la conformidad estricta contra el DTD oficial de NISO JATS y las directrices de indexación de la National Library of Medicine.

La aplicación integra dentro de su interfaz herramientas directas para facilitar la auditoría en estas plataformas (copiado rápido de XML, descarga de paquetes ZIP normados y enlaces directos a los validadores).

---

## 10. Proyección hacia el Trabajo Final de Integración (TFI)

El desarrollo realizado en estas Prácticas Profesionalizantes sienta las bases sólidas para el **Trabajo Final de Integración (TFI)** de la Licenciatura en Sistemas de la UNLa:

### 10.1 Definición y Relevancia del Problema
En el ecosistema científico actual, los investigadores redactan sus manuscritos en procesadores de texto comerciales (predominantemente Microsoft Word) que producen texto no estructurado. Por otro lado, la indexación en bases de datos de alto impacto (SciELO, PubMed Central, Redalyc, Latindex) y la publicación en plataformas como Open Journal Systems (OJS) exigen el estándar semántico JATS XML.

La marcación manual o mediante herramientas comerciales propietarias presenta tres problemas críticos para las universidades públicas:
1. **Altos costos operativos:** Las herramientas propietarias imponen licencias onerosas o tarifas por artículo procesado.
2. **Curva de aprendizaje pronunciada:** Los editores deben capacitarse en complejas herramientas de marcado sintáctico etiqueta por etiqueta.
3. **Pérdida de soberanía tecnológica:** Dependencia de proveedores externos para cumplir con los estándares de indexación.

### 10.2 Hipótesis y Aporte del TFI
El TFI profundizará en:
- El diseño de algoritmos heurísticos avanzados para la extracción semántica automatizada de metadatos en lenguas española y portuguesa.
- La consolidación de una plataforma web libre y autogestionada (modelo *"I Love XML-JATS"*) orientada a editoriales universitarias públicas.
- La integración directa mediante APIs con plataformas de gestión editorial como **OJS (Open Journal Systems)** y validadores automáticos Schematron.

---

## 11. Guía de Ejecución y Pruebas

### 11.1 Requisitos del Sistema
- Java 17 o superior.
- Maven 3.8+ instalado en el entorno.
- Node.js 18+ y npm.

### 11.2 Ejecución del Backend
```bash
cd backend
mvn clean test        # Ejecuta las pruebas unitarias y de integración
mvn spring-boot:run   # Inicia el servidor en http://localhost:8080 (o 8081 según configuración)
```

### 11.3 Ejecución del Frontend
```bash
cd frontend
npm install
npm run dev           # Inicia el entorno de desarrollo en http://localhost:5173
```

---

## 12. Trabajo Realizado el 29 de Septiembre de 2026

Commit de referencia: `5427438` — *"subiendo nuevas funcionalidades de validacion del xml en la ui y cambios"*

Este día concentró el mayor salto funcional de la herramienta: **la extensión completa del modelo de datos y del formulario de edición al esquema de 4 Bloques del estándar multirevista**, junto con la integración del Centro de Calidad Editorial y múltiples mejoras de robustez.

---

### 12.1 Extensión del DTO [FrontFields.java](backend/src/main/java/com/converter/docxjats/dto/FrontFields.java): Modelo de 4 Bloques

El DTO `FrontFields` fue completamente rediseñado para mapear la **totalidad** de los metadatos que componen el elemento raíz `<article>` y la sección `<front>` del estándar JATS/SciELO SPS, organizados en cuatro bloques editoriales:

| Bloque | Atributos / Elementos XML | Campos Java agregados |
|:---|:---|:---|
| **Bloque 1 · Tipo de Artículo** | `<article article-type="...">` | `articleType` |
| **Bloque 2 · Estándares Raíz** | `dtd-version`, `specific-use` (SPS), `xml:lang` | `dtdVersion`, `spsVersion`, `xmlLang` |
| **Bloque 3 · Revista, Editorial, DOI y Sección** | `<journal-title>`, `<abbrev-journal-title>`, `<journal-id nlm-ta>`, `<journal-id publisher-id>`, `<issn ppub/epub>`, `<publisher-name>`, `<article-id doi>`, `<subject>` | `journalTitle`, `journalAbbrev`, `journalIdNlmTa`, `journalIdPublisher`, `issnPpub`, `issnEpub`, `publisherName`, `doi`, `subject` |
| **Bloque 4 · Fechas, Numeración, Historial y Licencias** | `<pub-date>` (electrónica y colección), `<volume>`, `<issue>`, `<elocation-id>`, `<history>` (received, accepted, pub), `<license>`, `<copyright-statement>` | `pubDay/Month/Year`, `collectionYear`, `volume`, `issue`, `elocationId`, `publisherId`, `historyReceivedDay/Month/Year`, `historyAcceptedDay/Month/Year`, `historyPubDay/Month/Year`, `licenseType`, `licenseUrl`, `licenseText`, `copyrightStatement` |

Cada campo tiene getter/setter con valor por defecto seguro (sin `null`) para garantizar serialización JSON correcta.

---

### 12.2 Extensión del Servicio [JatsFrontEditor.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsFrontEditor.java): Lectura y Escritura de los 4 Bloques

El servicio de edición in-place fue profundamente ampliado para:

**Método `extract()` — Lectura desde el DOM XML:**
- **Bloque 1 y 2:** Lectura de los atributos del elemento raíz `<article>` (`article-type`, `dtd-version`, `specific-use`, `xml:lang`).
- **Bloque 3:** Iteración de los nodos `<journal-id>` con distinción por atributo `journal-id-type` (`nlm-ta` y `publisher-id`). Lectura de ISSN distinguiendo `pub-type="ppub"` y `pub-type="epub"`.
- **Bloque 4:** Iteración de `<pub-date>` distinguiendo por `date-type="pub"` y `date-type="collection"`. Iteración completa de `<history><date>` (received, accepted, pub). Lectura de `<license>` extrayendo `license-type`, `xlink:href` y el texto del nodo `<license-p>`.

**Método `applyFront()` — Escritura/Actualización en el DOM:**
- **Bloque 1 y 2:** Actualización directa de los atributos del elemento raíz `<article>` vía `article.setAttribute(...)`.
- Nuevo método privado `updateJournalMeta()`: actualiza o crea el árbol `<journal-title-group>` con `<journal-title>` y `<abbrev-journal-title abbrev-type="publisher">`, gestiona los nodos `<journal-id>`, actualiza los `<issn>` y el `<publisher-name>`.
- Nuevo método privado `updateArticleMeta()`: actualiza `<volume>`, `<elocation-id>`, `<subject>`, las fechas `<pub-date date-type="pub">` y `<pub-date date-type="collection">`, el `<history>` completo con sus tres fechas, y el bloque `<permissions>` (licencia CC y copyright).
- Nuevo método privado `updateHistoryDate()`: crea o actualiza un nodo `<date date-type="...">` con los campos `<day>`, `<month>` y `<year>` verificando si el elemento ya existe antes de crear uno nuevo.

---

### 12.3 Corrección en [XmlUtils.java](backend/src/main/java/com/converter/docxjats/service/jats/XmlUtils.java)

Se simplificó el método `escapeAttribute()` eliminando el reemplazo redundante de comillas tipográficas curvas (`"`, `"`, `'`, `'`), ya que el método `escape()` base cubre correctamente todos los caracteres especiales XML requeridos. Esto evitaba un doble-escape innecesario que podía corromper atributos con comillas decorativas en títulos.

---

### 12.4 Nuevas Pruebas Automatizadas en [JatsControllerTest.java](backend/src/test/java/com/converter/docxjats/controller/JatsControllerTest.java)

Se agregó el test `testFrontEditorModifiesFourBlocks()` que verifica el ciclo completo de lectura y escritura de los 4 Bloques:

```java
// Extracción → Verificación inicial
FrontFields fields = JatsFrontEditor.extract(baseXml);
assertThat(fields.getArticleType(), is("research-article"));
assertThat(fields.getJournalTitle(), is("Salud Colectiva"));

// Mutación de los 4 Bloques
fields.setArticleType("review-article");   // Bloque 1
fields.setDtdVersion("1.2");               // Bloque 2
fields.setSpsVersion("sps-1.10");          // Bloque 2
fields.setDoi("10.18294/sc.2026.9999");    // Bloque 3
fields.setSubject("Revisión Temática");    // Bloque 3
fields.setVolume("23");                    // Bloque 4
fields.setElocationId("e9999");            // Bloque 4
fields.setLicenseUrl("https://creativecommons.org/licenses/by/4.0/"); // Bloque 4

// Verificación en el XML resultante
String updatedXml = JatsFrontEditor.applyFront(baseXml, fields);
assertThat(updatedXml, containsString("article-type=\"review-article\""));
assertThat(updatedXml, containsString("specific-use=\"sps-1.10\""));
assertThat(updatedXml, containsString("<article-id pub-id-type=\"doi\">10.18294/sc.2026.9999</article-id>"));
assertThat(updatedXml, containsString("<volume>23</volume>"));
assertThat(updatedXml, containsString("xlink:href=\"https://creativecommons.org/licenses/by/4.0/\""));
```

---

### 12.5 Expansión de la UI en [App.jsx](frontend/src/App.jsx): Formulario Multirevista de 4 Bloques

La interfaz de usuario fue profundamente renovada para reflejar la arquitectura de 4 Bloques:

#### a) Barra de Plantillas Rápidas (`preset-bar`)
Se incorporaron dos perfiles predefinidos accesibles con un clic:
- **Plantilla "Salud Colectiva (UNLa)"** (`PRESET_SALUD_COLECTIVA`): precarga todos los campos institucionales de la revista (ISSN `1669-2381` / `1851-8265`, journal-id `scol`, publisher UNLa, volumen `22`, `article-type` research-article, SPS 1.9, licencia CC BY 4.0, etc.).
- **Plantilla "Revista Genérica / Multirevista"** (`PRESET_GENERICO`): precarga valores neutros para cualquier publicación arbitraria.

El botón activo se destaca visualmente con la clase `.preset-btn.active` (fondo borravino institucional).

#### b) Reestructuración en 5 Block-Cards (`.block-card`)
El formulario dejó de ser una lista plana de campos y se organizó en tarjetas visuales separadas, con encabezados etiquetados (`<span class="block-tag">`) y enlaces directos a la Tag Library de NLM cuando aplica:

| Bloque / Card | Contenido | Ref. NLM |
|:---|:---|:---:|
| **Bloque 1 · Tipo de Artículo** | `<select>` con los 8 tipos JATS (`research-article`, `review-article`, `editorial`, `letter`, `case-report`, `brief-report`, `book-review`, `other`) | ✓ |
| **Bloque 2 · Estándares Raíz** | DTD version (`1.1`, `1.2`, `1.3`), SPS version (`sps-1.9`, `sps-1.10`), Idioma del XML (`es`, `pt`, `en`) | — |
| **Bloque 3 · Revista, Publicador, DOI & Sección** | journal-title, abbrev-journal-title, journal-id (nlm-ta y publisher-id), ISSN ppub/epub, publisher-name, DOI, publisher-id, subject | — |
| **Bloque 4 · Fechas, Numeración, Historial & Licencias** | pub-date (día, mes, año electrónico + año de colección), volumen, fascículo, elocation-id, history (received, accepted, pub), selector de licencia CC, copyright | ✓ |
| **Bloque 5 · Contenido del Manuscrito & Autores** | articleTitle, subtitle, transTitle, abstractEs, abstractEn, keywordsEs, keywordsEn, gestión dinámica de autores | — |

#### c) Selector de Licencias Creative Commons (`CC_LICENSES`)
Se implementó un `<select>` desplegable para elegir la licencia sin necesidad de ingresar la URL manualmente:

```javascript
const CC_LICENSES = {
  'by-4.0':     { url: 'https://creativecommons.org/licenses/by/4.0/', ... },
  'by-nc-4.0':  { url: 'https://creativecommons.org/licenses/by-nc/4.0/', ... },
  'by-nc-nd-4.0': { ... },
  'by-sa-4.0':  { ... },
}
```
Al seleccionar una opción, la URL y el texto legal en español se precargan automáticamente en los campos de `licenseUrl` y `licenseText`.

#### d) Centro de Calidad Editorial (`quality-sheet`)
Se agregó un panel dedicado al control de calidad del XML generado, accesible en todo momento:
- **Botón "Validar en SciELO Stylechecker ↗":** abre directamente [https://manager.scielo.org/tools/validators/stylechecker/](https://manager.scielo.org/tools/validators/stylechecker/) para auditoría formal de conformidad SPS.
- **Botón "Validar en PubMed Central Stylechecker ↗":** abre directamente [https://pmc.ncbi.nlm.nih.gov/tools/stylechecker/](https://pmc.ncbi.nlm.nih.gov/tools/stylechecker/) para validación contra el DTD oficial NISO/NLM.
- **Botón "📋 Copiar XML al Portapapeles":** copia el XML completo generado al portapapeles del sistema operativo vía `navigator.clipboard.writeText()`, facilitando el pegado directo en los validadores o en herramientas externas. Muestra feedback visual ("✓ ¡XML Copiado!") durante 2,5 segundos.

#### e) Robustez de Parseo XML en el Cliente
Las funciones `getXmlTagValue()` y `parseAuthorsFromXml()` fueron envueltas en bloques `try/catch`, evitando que un XML malformado o con DTD referenciada en línea cause una excepción sin manejar y congele la interfaz.

#### f) Sincronización de Campos con el Backend
En la llamada a `handleApplyFront()`, se mapean correctamente los nombres de los campos del estado de React a los del DTO Java:
```javascript
const payload = {
  ...front,
  transTitle: front.transTitleEn || front.transTitle,       // trans-title
  abstractText: front.abstractEs || front.abstractText,      // abstract
  transAbstractText: front.abstractEn || front.transAbstractText, // trans-abstract
}
```

---

### 12.6 Nuevos Estilos CSS en [index.css](frontend/src/index.css)

Se incorporaron los estilos para todos los elementos nuevos de UI:

| Clase CSS | Propósito |
|:---|:---|
| `.block-card` | Tarjeta de cada bloque de metadatos con borde redondeado y sombra sutil. |
| `.block-header` | Cabecera de la card con separador inferior y distribución space-between. |
| `.block-tag` | Etiqueta monoespaciada con fondo borravino semitransparente, tipo chip editorial. |
| `.block-link` | Enlace discreto a la Tag Library NLM, con hover en color acento. |
| `.preset-bar` | Barra punteada con fondo crema y botones de plantilla rápida. |
| `.preset-btn` | Botón de plantilla con variante `.active` (fondo borravino + texto blanco). |
| `.form-select` | `<select>` con tipografía Inter consistente con el resto del formulario. |
| `.quality-sheet` | Panel de calidad con acento azul (`#2b6cb0`) para diferenciarlo del flujo principal. |
| `.quality-header`, `.quality-info` | Cabecera y texto descriptivo del Centro de Calidad. |
| `.stylechecker-actions` | Contenedor flex de los botones de validación. |
| `.btn-validator` | Botón base de validación con flex, gap y transición. |
| `.scielo-btn` | Variante roja para SciELO. |
| `.pmc-btn` | Variante verde para PubMed Central. |
| `.copy-btn` | Variante gris para el botón de copia al portapapeles. |

---

### 12.7 Resumen de Cambios del Día en Cifras

| Archivo | Líneas añadidas | Líneas eliminadas | Neto |
|:---|---:|---:|---:|
| `frontend/src/App.jsx` | +1.166 | -444 | +722 |
| `frontend/src/index.css` | +189 | 0 | +189 |
| `backend/.../dto/FrontFields.java` | +351 | -8 | +343 |
| `backend/.../jats/JatsFrontEditor.java` | +508 | -80 | +428 |
| `backend/.../controller/JatsControllerTest.java` | +49 | 0 | +49 |
| `backend/.../jats/XmlUtils.java` | +1 | -6 | −5 |
| **Total** | **+2.264** | **−538** | **+1.726** |

---

## 13. Guía de Ejecución y Pruebas

### 13.1 Requisitos del Sistema
- Java 17 o superior.
- Maven 3.8+ instalado en el entorno.
- Node.js 18+ y npm.

### 13.2 Ejecución del Backend
```bash
cd backend
mvn clean test        # Ejecuta las pruebas unitarias y de integración
mvn spring-boot:run   # Inicia el servidor en http://localhost:8080 (o 8081 según configuración)
```

### 13.3 Ejecución del Frontend
```bash
cd frontend
npm install
npm run dev           # Inicia el entorno de desarrollo en http://localhost:5173
```

---

## 15. Optimización de Conformidad con SciELO SPS 1.9 y PMC StyleChecker, Deshardcodeo del Backend y Rediseño del Editor XML (Noviembre 2026)

Durante esta fase de trabajo se resolvieron los desafíos más complejos de interoperabilidad editorial, eliminando cualquier lógica atada a identificadores específicos y alcanzando la conformidad estricta tanto con el **SciELO Publishing Schema (SPS 1.9)** como con el **PMC StyleChecker (National Library of Medicine)**.

```mermaid
flowchart TD
    subgraph Diagnóstico y Detección
        A[Validación de Artículo 6032] --> B{Motores de Calidad}
        B -->|PubMed Central| C[Errores DTD & PMC StyleChecker]
        B -->|SciELO Manager| D[Errores SPS Schematron]
    end

    subgraph Soluciones PMC StyleChecker
        C --> C1["Normalización ORCID: https://orcid.org/XXXX"]
        C --> C2["Sanitización DOI: strip de espacios internos"]
        C --> C3["Bio DTD compliance: texto envuelto en &lt;p&gt;"]
        C --> C4["Notas de tabla: asignación fn-type='other'"]
    end

    subgraph Soluciones SciELO SPS 1.9
        D --> D1{¿Tiene contratos o subsidios?}
        D1 -->|Sí: Art. 5939| D2["&lt;front&gt;: &lt;funding-group&gt; con &lt;award-group&gt;<br>&lt;back&gt;: fn-type='financial-disclosure'"]
        D1 -->|No / Sin Financiamiento: Art. 6032| D3["&lt;front&gt;: Sin &lt;funding-group&gt; huérfano<br>&lt;back&gt;: fn-type='supported-by'"]
    end

    subgraph Deshardcodeo Arquitectural
        E[JatsFrontBuilder] --> F["applySmartDefaults() dinámico"]
        G[JatsPatternService] --> H["Escaneo y matching dinámico en archivosXMLPatron/"]
    end
```

---

### 15.1 Deshardcodeo Completo del Backend

Se eliminó cualquier dependencia rígida respecto de IDs de artículos específicos (`6032`, `5939`), transformando el conversor en un motor verdaderamente genérico:

1. **Refactorización de [JatsFrontBuilder.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsFrontBuilder.java):**
   - Se eliminaron por completo los métodos `applyArticle6032Defaults()`, `applyArticle5939Defaults()`, `isArticle6032()` y `isArticle5939()`.
   - Se implementó `applySmartDefaults()`: infiere dinámicamente el `elocation-id` y el año de colección a partir del DOI extraído o metadatos contextuales mediante expresiones regulares, sin fijar constantes numéricas en el código.
2. **Generalización de [JatsPatternService.java](backend/src/main/java/com/converter/docxjats/service/JatsPatternService.java):**
   - Se eliminaron los bloques condicionales `if ("6032".equals(id))`.
   - Se implementó un algoritmo dinámico que inspecciona el directorio `archivosXMLPatron/` en tiempo de ejecución, ordenando los archivos encontrados por cercanía semántica (`-e{id}.xml`, `_{id}.xml`, `{id}.xml`), permitiendo incorporar nuevos artículos patrón sin tocar una sola línea de código Java.
3. **Controlador Limpio [JatsEndpointController.java](backend/src/main/java/com/converter/docxjats/controller/JatsEndpointController.java):**
   - Remoción de textos fijos en mensajes de excepción y respuestas JSON.

---

### 15.2 Conformidad con PubMed Central (PMC) StyleChecker y DTD NISO JATS

El validador oficial de PMC de la National Library of Medicine reportó discrepancias que fueron resueltas de raíz:

| Problema Reportado por PMC | Causa Raíz en el Código / XML | Solución Técnica Implementada |
| :--- | :--- | :--- |
| **ORCID no cumple directrices NLM:** *"Los valores de ORCID deben comenzar con 'http://orcid.org/' o 'https://orcid.org/'"* | `JatsFrontBuilder.normalizeOrcid()` eliminaba el prefijo HTTP/HTTPS para guardar solo el ID de 16 dígitos. | Se reformuló `normalizeOrcid()` para que limpie cualquier prefijo previo o mal formado y garantice **siempre la URL canónica:** `https://orcid.org/XXXX-XXXX-XXXX-XXXX`. |
| **DOI malformado con espacios internos:** `10.1097/01.aog.0000 102935.91389.53` (Ref. B60) y `10.3109/ 00016349...` (Ref. B61) | Espacios en blanco o saltos de línea introducidos en el DOCX original durante la diagramación. | Se incorporó sanitización automática en [JatsBackBuilder.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsBackBuilder.java) con `sanitizedDoi = ref.doi().replaceAll("\\s+", "")` previo a la emisión del nodo `<pub-id pub-id-type="doi">`. |
| **Error DTD en biografías:** *"Element bio content no sigue la DTD"* | Texto de biografías emitido directamente como nodo de texto dentro de `<bio>`. El DTD JATS 1.1 exige que el contenido esté contenido en párrafos o estructuras de bloque. | Envoltura estricta de todo el contenido de `<bio>` dentro de etiquetas `<p>...</p>`. |
| **Entidad HTML no declarada en DTD:** `&nbsp;` | Uso de entidad de espacio no rompible de HTML en celdas de tabla. | Reemplazo sistemático por la entidad numérica decimal canónica en XML: `&#160;`. |
| **Atributo requerido en notas de tabla:** `<fn id="TFN1">` | La especificación SPS y el DTD exigen clasificar el tipo de nota. | Asignación de `fn-type="other"` a todas las notas asociadas a tablas. |

---

### 15.3 Conformidad con SciELO Publishing Schema (SPS 1.9): Lógica de Financiamiento

Durante la validación en el validador oficial de SciELO (`manager.scielo.org`), surgieron dos errores correlacionados sobre las declaraciones de financiamiento:

> `STYLE ERROR: [Element 'fn': Missing element funding-statement.]`  
> `STYLE ERROR: [Element 'fn-group': This element has occurrences not declared in funding-group.]`  
> `STYLE ERROR: [Element 'funding-group': Missing element award-group.]`

#### Análisis Normativo de SciELO SPS 1.9:
1. En SciELO SPS, el elemento `<funding-group>` dentro de `<article-meta>` **requiere de forma obligatoria al menos un elemento `<award-group>`** con su `<funding-source>` (agencia) y `<award-id>` (número de contrato/beca). Un `<funding-group>` que contiene solo `<funding-statement>` es **inválido**.
2. Cuando un artículo **no cuenta con financiamiento externo o subsidios con número de contrato** (como el artículo 6032: *"Este trabajo se realizó sin financiamiento específico"*):
   - **En `<front>` (`<article-meta>`):** **NO** debe emitirse el bloque `<funding-group>`.
   - **En `<back>` (`<fn-group>`):** La nota de pie de página **NO** debe llevar `fn-type="financial-disclosure"` (ya que este atributo está reservado para declaraciones vinculadas a subsidios/contratos indexados en el front), sino **`fn-type="supported-by"`**, que es la directiva oficial de SciELO para apoyos o declaraciones sin número de contrato.
3. Cuando un artículo **sí cuenta con contratos o becas explícitas** (como el artículo 5939, financiado por CAPES y FAPESC):
   - **En `<front>`:** Se emite `<funding-group>` con sus respectivos `<award-group award-type="contract">` y `<funding-statement>`.
   - **En `<back>`:** Se emite `<fn fn-type="financial-disclosure">`.

#### Implementación en el Motor:
- **[JatsFrontBuilder.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsFrontBuilder.java):** `buildFunding()` verifica `if (fundingSources.isEmpty()) return;`, evitando la generación errónea de `<funding-group>` cuando no hay contratos que declarar.
- **[DocxToJatsConverter.java](backend/src/main/java/com/converter/docxjats/service/DocxToJatsConverter.java):** Discierne automáticamente entre `financial-disclosure` (cuando hay subsidios identificados) y `supported-by` (cuando se trata de una declaración general o sin financiamiento).
- **[JatsBackBuilder.java](backend/src/main/java/com/converter/docxjats/service/jats/JatsBackBuilder.java):** Incorporación formal del tipo de nota `SUPPORTED_BY("supported-by")` al enumerador `FootnoteType` y normalización de tipos.
- **[JatsPatternService.java](backend/src/main/java/com/converter/docxjats/service/JatsPatternService.java):** Método `normalizeFunding()` que analiza el XML servido: si no contiene `<award-group`, remueve cualquier `<funding-group>` huérfano y normaliza la nota del `<back>` a `supported-by`. Si contiene `<award-group` (art. 5939), mantiene la estructura completa intacta.

---

### 15.4 Rediseño del Editor XML en el Frontend

Para optimizar el flujo de trabajo editorial de los usuarios que inspeccionan o ajustan el marcado XML en la interfaz web:
- Se implementó un visor con numeración de líneas fija y sincronizada con el scroll.
- Tipografía monoespaciada de alta legibilidad (*JetBrains Mono*).
- Barra de herramientas con copiado rápido al portapapeles, descarga directa y feedback visual claro en caso de errores de validación.

---

### 15.5 Suite de Pruebas Automatizadas

Se actualizaron y ejecutaron las pruebas unitarias y de integración en [JatsControllerTest.java](backend/src/test/java/com/converter/docxjats/controller/JatsControllerTest.java) y [RunRendererTest.java](backend/src/test/java/com/converter/docxjats/service/jats/RunRendererTest.java):

```
[INFO] Running com.converter.docxjats.controller.JatsControllerTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.078 s
[INFO] Running com.converter.docxjats.service.jats.RunRendererTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.845 s
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 16. Conclusión y Estado de Cumplimiento

El trabajo realizado en la Práctica Profesionalizante cumple de manera exhaustiva con los objetivos de:
1. **Conformidad Estándar:** Generación de marcado XML alineado con el esquema oficial **SciELO Publishing Schema (SPS 1.9 / 1.10)** y **JATS 1.1 - 1.3**, superando con éxito las validaciones en los Stylecheckers oficiales de **SciELO** y **PubMed Central (PMC)**.
2. **Modularidad y Mantenibilidad:** Código estructurado en capas con separación neta entre lectura de modelos Word, representación de datos y generación XML, completamente deshardcodeado de identificadores de artículos puntuales.
3. **Calidad Editorial:** Soporte para artículos reales y complejos de la revista *Salud Colectiva*, cubriendo elementos gráficos, tablas, jerarquías de secciones, notas críticas y referencias bibliográficas analizadas granularmente.
4. **Herramienta Multirevista:** Sistema de plantillas rápidas y formulario organizado en 4 Bloques que permite parametrizar cualquier publicación científica académica con valores predefinidos o personalizados.
5. **Integración con Validadores Externos:** El Centro de Calidad Editorial integra acceso directo a los Stylecheckers oficiales de **SciELO** y **PubMed Central (PMC)**, más copiado de XML al portapapeles para un flujo de auditoría sin fricción.
6. **Herramienta Integrada:** Entrega de una aplicación web completa con previsualización, edición in-place y exportación lista para su incorporación al flujo de producción editorial en plataformas como Open Journal Systems (OJS).
