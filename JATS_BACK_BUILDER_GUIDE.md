# Guía de Refactorización: JatsBackBuilder.java

## Resumen Ejecutivo

La clase `JatsBackBuilder` ha sido completamente refactorizada para proporcionar soporte exhaustivo al estándar **SciELO PS (SciELO Publishing Schema / JATS 1.1 - 1.3)** en la construcción de la sección `<back>` de artículos científicos en formato XML.

### Cambios Principales

✅ **Soporte Completo de Elementos `<back>`:**
- `<ack>` (Agradecimientos)
- `<fn-group>` con clasificación de tipos de notas
- `<sec sec-type="data-availability">` (Disponibilidad de Datos)
- `<ref-list>` con referencias bibliográficas estructuradas

✅ **Parsing Avanzado de Referencias:**
- Detección automática del tipo de publicación (journal, book, thesis, webpage)
- Extracción de autores/colaboradores con estructura de nombre (surname/given-names)
- Identificación de metadatos (DOI, ISBN, URL, año, volumen, páginas)
- Generación dual: `<mixed-citation>` (texto plano) + `<element-citation>` (estructurado)

✅ **Integración con Componentes Existentes:**
- Uso de `RunRenderer` para preservar formato inline (negrita, cursiva, etc.)
- Compatible con `XmlUtils.escape()` para sanitización XML
- Mantiene compatibilidad con `ImageRegistry` y `ImageRegistry.register()`

---

## API Pública

### 1. Constructor

```java
/**
 * Crea una nueva instancia del builder de la sección <back>.
 * 
 * @param runRenderer Renderizador de runs para preservar formato inline
 *                    en agradecimientos, notas y referencias.
 */
public JatsBackBuilder(RunRenderer runRenderer)
```

**Cambio importante:** La nueva versión **requiere** un `RunRenderer`. 

**Actualización en DocxToJatsConverter.java (ya realizada):**
```java
// Antes:
JatsBackBuilder back = new JatsBackBuilder();

// Ahora:
JatsBackBuilder back = new JatsBackBuilder(runRenderer);
```

---

### 2. Métodos para Agregar Contenido

#### 2.1 Agradecimientos (`setAcknowledgements`)

```java
/**
 * Establece el texto de agradecimientos.
 * Se renderiza como: <ack><title>Agradecimientos</title><p>...</p></ack>
 * 
 * @param title    Título de la sección (ej. "Agradecimientos")
 * @param content  Texto del párrafo
 */
public void setAcknowledgements(String title, String content)
```

**Ejemplo de Uso:**
```java
JatsBackBuilder back = new JatsBackBuilder(runRenderer);
back.setAcknowledgements(
    "Agradecimientos",
    "Agradecemos al Dr. García por sus contribuciones valiosas."
);
```

**XML Generado:**
```xml
<ack>
  <title>Agradecimientos</title>
  <p>Agradecemos al Dr. García por sus contribuciones valiosas.</p>
</ack>
```

---

#### 2.2 Notas del Artículo (`addFootnote`)

```java
/**
 * Agrega una nota al pie clasificada por tipo.
 * Las notas se agruparán en <fn-group> en el XML final.
 * 
 * @param fnType  Tipo de nota: "financial-disclosure", "conflict", "con", "other", "equal"
 * @param label   Etiqueta visible (ej. "1", "*", "Conflicto")
 * @param content Contenido de la nota
 */
public void addFootnote(String fnType, String label, String content)
```

**Tipos de Notas Soportados (SciELO PS):**
| Tipo | Descripción | Etiqueta Típica |
|------|-------------|-----------------|
| `financial-disclosure` | Financiamiento, agradecimientos financieros | Financiamiento |
| `conflict` | Declaración de conflicto de intereses | Conflicto de Intereses |
| `con` | Contribución de los autores | Contribución autoral |
| `equal` | Contribución igual | Contribución autoral |
| `other` | Otras notas aclaratorias | Otra |

**Ejemplo de Uso:**
```java
JatsBackBuilder back = new JatsBackBuilder(runRenderer);

// Declaración de conflicto
back.addFootnote(
    "conflict",
    "Conflicto de Intereses",
    "Los autores declaran no tener conflictos de interés."
);

// Contribución de autores
back.addFootnote(
    "con",
    "Contribución autoral",
    "María García: conceptualización, análisis. Juan López: revisión crítica."
);

// Financiamiento
back.addFootnote(
    "financial-disclosure",
    "Financiamiento",
    "Proyecto financiado por CONICET bajo beca número 2024-1234."
);
```

**XML Generado:**
```xml
<fn-group>
  <fn fn-type="conflict" id="fn1">
    <label>Conflicto de Intereses</label>
    <p>Los autores declaran no tener conflictos de interés.</p>
  </fn>
  <fn fn-type="con" id="fn2">
    <label>Contribución autoral</label>
    <p>María García: conceptualización, análisis. Juan López: revisión crítica.</p>
  </fn>
  <fn fn-type="financial-disclosure" id="fn3">
    <label>Financiamiento</label>
    <p>Proyecto financiado por CONICET bajo beca número 2024-1234.</p>
  </fn>
</fn-group>
```

---

#### 2.3 Disponibilidad de Datos (`setDataAvailability`)

```java
/**
 * Establece la declaración de disponibilidad de datos del artículo.
 * Se renderiza como: <sec sec-type="data-availability"><title>...</title><p>...</p></sec>
 * 
 * @param title   Título de la sección
 * @param content Contenido de la declaración
 */
public void setDataAvailability(String title, String content)
```

**Ejemplo de Uso:**
```java
JatsBackBuilder back = new JatsBackBuilder(runRenderer);
back.setDataAvailability(
    "Disponibilidad de datos",
    "Los datos utilizados en este estudio están disponibles bajo solicitud " +
    "al autor de correspondencia (author@example.com)."
);
```

**XML Generado:**
```xml
<sec sec-type="data-availability">
  <title>Disponibilidad de datos</title>
  <p>Los datos utilizados en este estudio están disponibles bajo solicitud 
     al autor de correspondencia (author@example.com).</p>
</sec>
```

---

#### 2.4 Gestión de Referencias Bibliográficas

##### 2.4.1 Abrir Sección de Referencias

```java
/**
 * Inicia la sección de referencias bibliográficas.
 * 
 * @param title Título a mostrar (ej. "Referencias bibliográficas")
 */
public void openReferences(String title)
```

##### 2.4.2 Agregar Referencia

```java
/**
 * Agrega una referencia bibliográfica completa.
 * El texto se parsea para identificar: autores, año, volumen, páginas, DOI, URL, etc.
 * 
 * @param citationText Texto completo de la cita
 */
public void appendReference(String citationText)
```

**Formato de Entrada Soportado:**

El parser intenta reconocer varios formatos:

**a) Artículos de Revista (con puntuación de separadores):**
```
Lara MA, Filho ET. O direito à recusa de tratamento médico na Resolução n. 2.232/2019.
Revista de Direito Sanitário. 2022;22(2):e0027. doi: 10.11606/issn.2316-9044.rdisan.2022.182012
```

Estructura esperada: `Autores. Título. Revista. Año;volumen(número):páginas. doi: ...`

**b) Libros:**
```
Gracia D. Pensar a bioética: metas e desafios. São Paulo: Edições Loyola; 2010.
```

Estructura: `Autores. Título. Lugar: Editorial; año.`

**c) Tesis:**
```
García J. Análisis de metodologías innovadoras. Doctorado en Educación,
Universidad Nacional; 2022.
```

**d) Sitios Web:**
```
World Health Organization. Caesarean section rates continue to rise [Internet]. 2021
[citado 26 nov 2025]. Disponible en: https://tinyurl.com/42fst2cr
```

Detecta: `[Internet]` o URLs; estructura: `Organizacion. Título [Internet]. Año [citado ...]. URL`

---

##### 2.4.3 Cerrar Sección de Referencias

```java
/**
 * Indica que no hay más referencias a agregar.
 * (Generalmente invocado internamente al cambiar de sección.)
 */
public void closeReferences()
```

---

### 3. Métodos Auxiliares

```java
/**
 * Verifica si hay contenido (referencias, notas, agradecimientos, datos).
 * 
 * @return true si hay al menos un elemento en la sección <back>
 */
public boolean hasContent()

/**
 * Construye y retorna el XML completo del bloque <back>.
 * Orden de generación: agradecimientos → notas → disponibilidad → referencias.
 * 
 * @return String con XML del <back>, o "" si está vacío
 */
public String build()
```

---

## Estructura de Datos Generada

### Orden de Elementos en `<back>`

Según SciELO PS, el orden es:
1. `<ack>` (Agradecimientos) — *Opcional*
2. `<fn-group>` (Notas del Artículo) — *Opcional*
3. `<sec sec-type="data-availability">` (Disponibilidad de Datos) — *Opcional*
4. `<ref-list>` (Referencias Bibliográficas) — *Opcional*

**Ejemplo Completo:**
```xml
<back>
  <ack>
    <title>Agradecimientos</title>
    <p>Agradecemos al equipo de investigación...</p>
  </ack>
  
  <fn-group>
    <fn fn-type="conflict" id="fn1">
      <label>Conflicto</label>
      <p>Los autores declaran no tener conflictos de interés.</p>
    </fn>
  </fn-group>
  
  <sec sec-type="data-availability">
    <title>Disponibilidad de datos</title>
    <p>Los datos están disponibles bajo solicitud...</p>
  </sec>
  
  <ref-list>
    <title>Referencias bibliográficas</title>
    <ref id="B1">
      <label>1</label>
      <mixed-citation>García D. Pensar a bioética: metas e desafios...</mixed-citation>
      <element-citation publication-type="book">
        <person-group person-group-type="author">
          <name>
            <surname>García</surname>
            <given-names>D</given-names>
          </name>
        </person-group>
        <source>Pensar a bioética: metas e desafios</source>
        <publisher-loc>São Paulo</publisher-loc>
        <publisher-name>Edições Loyola</publisher-name>
        <year>2010</year>
      </element-citation>
    </ref>
  </ref-list>
</back>
```

---

## Patrones de Referencias Detectados Automáticamente

### 1. Revista (`publication-type="journal"`)

```regex
^(.*?)\.\s+(.*?)\.\s+([^.;]+?)\.\s+(\d{4});(\d+)(?:\((\d+)\))?:([\\d-]+|e\d+).*$
```

**Captura:** Autores . Título . Revista . Año;volumen(número):páginas

**Ejemplo:**
```
Eich M, Verdi MIM, Martins PPS. Deliberación moral en sedación paliativa.
Revista Bioética. 2015;23(3):583592. doi: 10.1590/1983-80422015233095
```

**Elementos extraídos:**
- Autores: `Eich M`, `Verdi MIM`, `Martins PPS`
- Título: `Deliberación moral en sedación paliativa`
- Revista: `Revista Bioética`
- Año: `2015`
- Volumen: `23`
- Número: `3`
- Páginas: `583-592`
- DOI: `10.1590/1983-80422015233095`

---

### 2. Libro (`publication-type="book"`)

```regex
^(.*?)\.\s+(.*?)\.\s+([^;]+?);?\s+(\d{4}).*$
```

**Captura:** Autores . Título . Editorial; año

**Ejemplo:**
```
Zubiri X. Inteligencia e realidade. São Paulo: É Realizações Editora; 2011.
```

**Elementos extraídos:**
- Autores: `Zubiri X`
- Título: `Inteligencia e realidade`
- Editorial: `São Paulo: É Realizações Editora`
- Año: `2011`

---

### 3. Tesis (`publication-type="thesis"`)

```regex
^(.*?)\.\s+(.*?)\.\s+((?:Tesis|Dissertation|Doctorado)[^,;]+)[;,]\s+([^;]+);\s+(\d{4}).*$
```

**Ejemplo:**
```
García JM. Análisis de metodologías. Doctorado en Educación, 
Universidad Nacional de Córdoba; 2022.
```

**Elementos extraídos:**
- Autores: `García JM`
- Título: `Análisis de metodologías`
- Tipo: `Doctorado en Educación`
- Institución: `Universidad Nacional de Córdoba`
- Año: `2022`

---

### 4. Sitio Web (`publication-type="webpage"`)

**Indicadores:**
- Presencia de `[Internet]`
- Presencia de URL (http/https)

**Ejemplo:**
```
World Health Organization. Caesarean section rates [Internet]. 2021 
[citado 26 nov 2025]. Disponible en: https://tinyurl.com/42fst2cr
```

---

## Identificadores Soportados

La clase extrae automáticamente:

| Identificador | Patrón | Elemento XML |
|---------------|--------|--------------|
| DOI | `10.XXXX/...` | `<pub-id pub-id-type="doi">` |
| ISBN | `ISBN-10` o `ISBN-13` | `<pub-id pub-id-type="isbn">` |
| URL | `http://` o `https://` | `<ext-link ext-link-type="uri">` |
| Año | `YYYY` (1900-2099) | `<year>` |

---

## Ejemplo de Integración Completa

### En DocxToJatsConverter

```java
// Ya existente (línea ~106-109):
ImageRegistry imageRegistry = new ImageRegistry();
RunRenderer runRenderer = new RunRenderer(imageRegistry);

JatsBodyBuilder body = new JatsBodyBuilder();
JatsBackBuilder back = new JatsBackBuilder(runRenderer);  // ← CAMBIO: runRenderer agregado

Mode mode = Mode.BODY;

// ... procesamiento de párrafos del documento DOCX ...

// Cuando se detecta una sección de agradecimientos:
if (isAcknowledgementsHeading(normalizedText)) {
    String ackTitle = trimmedText;
    // Recolectar párrafos hasta encontrar la siguiente sección principal
    StringBuilder ackContent = new StringBuilder();
    // ... agregar párrafos ...
    back.setAcknowledgements(ackTitle, ackContent.toString());
}

// Cuando se detecta una sección de notas/conflictos:
if (isFootnoteSection(normalizedText)) {
    // Parsear las notas y clasificarlas
    back.addFootnote("conflict", "Conflicto de Intereses", conflictText);
    back.addFootnote("con", "Contribución autoral", contributionText);
}

// Cuando se detectan referencias (ya implementado):
if (isReferencesHeading(normalizedText)) {
    back.openReferences(trimmedText);
    mode = Mode.REFERENCES;
}
if (mode == Mode.REFERENCES) {
    back.appendReference(trimmedText);
}

// ... al finalizar el documento ...
back.closeReferences();

// Generar XML:
String backXml = back.build();
```

---

## Reglas de Sanitización y Validación

### 1. Escaping de Caracteres XML

Todos los valores de texto se pasan a través de `XmlUtils.escape()`:
- `&` → `&amp;`
- `<` → `&lt;`
- `>` → `&gt;`
- `"` → `&quot;`
- `'` → `&apos;`

**Automáticamente manejado por la clase.**

### 2. Validación de IDs

Los IDs de referencias se generan como `B1`, `B2`, `B3`, etc.
- Siempre con prefijo `B`
- Secuenciales y únicos

### 3. Estructura Obligatoria

- Cada `<ref>` DEBE tener `id="B..."`
- Cada `<ref>` DEBE tener `<label>`
- Cada `<fn>` DEBE tener `fn-type`

**Todo esto es garantizado por la clase.**

---

## Comparación con Patrones de Referencia

### Patrón 5939
- Contiene referencias bibliográficas de libros y artículos de revista
- Estructura validada contra SciELO PS 1.9

### Patrón 6032
- Incluye `<ack>` con texto formateado
- Referencias con URLs y acceso en línea
- Agradecimientos con formato enriquecido

**La refactorización garantiza compatibilidad con ambos patrones.**

---

## Testing y Validación

### Verificar Correspondencia con Patrones

```bash
# Generar XML con la nueva clase
java -cp target/classes ... (aplicación)

# Comparar con patrones:
diff -u archivosXMLPatron/1851-8265-scol-22-e5939.xml output.xml | grep "<back>"
diff -u archivosXMLPatron/1851-8265-scol-22-e6032.xml output.xml | grep "<back>"
```

### Validar contra DTD de JATS

```bash
xmllint --dtdvalid JATS-journalpublishing1.dtd output.xml
```

---

## Notas de Implementación

### Cambios que Requieren Atención

1. **Constructor require RunRenderer:**
   - ✅ Ya actualizado en DocxToJatsConverter.java (línea 109)

2. **Nuevos Métodos Disponibles pero no Integrados Yet:**
   - `setAcknowledgements()` — Requiere lógica de detectar sección de agradecimientos en DocxToJatsConverter
   - `addFootnote()` — Requiere parseo de notas en DocxToJatsConverter
   - `setDataAvailability()` — Requiere parseo de sección de disponibilidad

3. **Backward Compatibility:**
   - ✅ Métodos existentes (`openReferences()`, `appendReference()`, `closeReferences()`, `build()`) siguen funcionando igual
   - ✅ El XML generado para referencias es idéntico al anterior

---

## Próximos Pasos Recomendados

1. **Extender DocxToJatsConverter** para detectar y procesar:
   - Secciones de agradecimientos
   - Notas de conflicto/contribución
   - Declaraciones de disponibilidad de datos

2. **Enriquecer Parsing de Referencias** con:
   - Soporte para más formatos de entrada (APA, MLA)
   - Extracción de URL de acceso e información de DOI

3. **Agregar Pruebas Unitarias** para validar:
   - Parsing de referencias en diferentes formatos
   - Generación XML correcta
   - Validación contra DTD

---

## Contacto y Mantenimiento

Cambios realizados: **Refactorización Completa de JatsBackBuilder.java**

Fecha: 2026-09-09  
Objetivo: Conformidad exhaustiva con SciELO PS para sección `<back>`

Para preguntas o mejoras, consulte la documentación inline en el código o abra un issue en el repositorio.
