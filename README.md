# DOCX → JATS XML

Conversor de documentos Word (`.docx`) a XML **JATS** (Journal Article Tag Suite),
con backend en **Spring Boot** (Apache POI) y frontend en **React** (Vite).

## Estructura

```
docx-to-jats/
├── backend/     Spring Boot (Java 17, Maven)
└── frontend/    React + Vite
```

## Cómo correrlo

### 1. Backend

```bash
cd backend
mvn spring-boot:run
```

Levanta en `http://localhost:8080`. Expone dos endpoints (multipart, campo `file`):

- `POST /api/convert/preview` → JSON `{ xml, imageCount }`, para ver el XML en pantalla.
- `POST /api/convert/xml` → descarga el XML JATS suelto (`<nombre>-jats.xml`), sin comprimir.
- `POST /api/convert/package` → descarga un `.zip` con `article.xml` + `images/` (útil si el manuscrito tiene imágenes y se necesitan los binarios).

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Levanta en `http://localhost:5173`. Si el backend corre en otra URL, definila con:

```bash
# frontend/.env
VITE_API_BASE=http://localhost:8080
```

## Qué convierte el parser

| Word (.docx)                              | JATS                                    |
|--------------------------------------------|------------------------------------------|
| Estilo "Title" / "Título"                  | `<article-title>`                        |
| "Heading 1..6" / "Título 1..6"             | `<sec>` anidados con `<title>`           |
| Negrita / cursiva / subrayado              | `<bold>` `<italic>` `<underline>`        |
| Superíndice / subíndice                    | `<sup>` `<sub>`                          |
| Listas con viñetas o numeradas             | `<list list-type="bullet\|order">`       |
| Tablas (1ª fila = encabezado)              | `<table-wrap><table><thead>/<tbody>`     |
| Imágenes embebidas (párrafo propio)        | `<fig><graphic xlink:href="images/…"/>`  |
| Imágenes dentro de un párrafo de texto     | `<inline-graphic>`                       |
| Heading "Resumen"/"Abstract"               | `<abstract>` (se mueve al front-matter)  |
| Heading "Referencias"/"Bibliografía"       | `<back><ref-list><ref><mixed-citation>`  |

Las imágenes se extraen del `.docx` y viajan dentro del `.zip` en `images/`,
referenciadas por `xlink:href` como pide JATS (no van embebidas en base64).

## Qué falta / puntos de extensión

Este es un punto de partida funcional, no una implementación JATS 100% completa.
Quedan afuera (marcados para extender en `DocxToJatsConverter.java`):

- Metadatos de autores, afiliaciones, DOI, fechas de publicación (`<contrib-group>`, etc.)
- Notas al pie y ecuaciones
- Citas bibliográficas estructuradas (hoy cada línea de "Referencias" se vuelca como texto
  plano dentro de `<mixed-citation>`, sin parsear autor/año/revista)
- Validación contra el DTD/XSD oficial de JATS (se recomienda correr el XML resultante
  por un validador como Java `oxygen` o `jats-conversion` antes de producción)

## Notas técnicas

- El backend usa **Apache POI** (`XWPFDocument`) para leer el `.docx` en memoria; no requiere
  Microsoft Word ni LibreOffice instalados.
- CORS está habilitado para `localhost:5173` y `localhost:3000` en `CorsConfig.java` — ajustalo
  si desplegás el frontend en otro dominio.
- Tamaño máximo de subida: 25 MB (`application.properties`).
