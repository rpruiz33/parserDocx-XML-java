import { useCallback, useRef, useState } from 'react'
import axios from 'axios'
import './index.css'
import logoUnla from './assets/unla-logo.png'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8081'

/**
 * Componente reutilizable para inputs que adaptan su ancho
 * automáticamente a la longitud de su texto.
 */
function AutoInput({ value = '', placeholder = '', minChars = 10, type = 'text', onChange, ...props }) {
  const currentText = String(value || placeholder || '')
  const widthChars = Math.max(currentText.length + 2, minChars)

  return (
    <input
      type={type}
      value={value}
      placeholder={placeholder}
      onChange={onChange}
      style={{
        width: `${widthChars}ch`,
        maxWidth: '100%',
        boxSizing: 'border-box'
      }}
      {...props}
    />
  )
}

function highlightXml(xml) {
  if (!xml) return ''
  const escaped = xml
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(/(&lt;\/?[a-zA-Z0-9\-:]+)/g, '<span class="tag">$1</span>')
}

function stripExtension(name) {
  if (!name) return 'documento'
  const idx = name.lastIndexOf('.')
  return idx > 0 ? name.slice(0, idx) : name
}

function getXmlTagValue(xml, tagName) {
  if (!xml) return ''
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  const node = Array.from(document.getElementsByTagName('*')).find((element) => element.localName === tagName)
  return node?.textContent?.trim() || ''
}

function firstDescendant(element, tagName) {
  return Array.from(element?.getElementsByTagName('*') || [])
    .find((node) => node.localName === tagName) || null
}

function descendants(element, tagName) {
  return Array.from(element?.getElementsByTagName('*') || [])
    .filter((node) => node.localName === tagName)
}

function elementText(element) {
  return element?.textContent?.replace(/\s+/g, ' ').trim() || ''
}

function formatAffiliation(affiliation) {
  if (!affiliation) return ''

  const institutions = descendants(affiliation, 'institution').map(elementText).filter(Boolean)
  const locations = ['city', 'state', 'country']
    .flatMap((tag) => descendants(affiliation, tag).map(elementText))
    .filter(Boolean)
  const values = [...new Set([...institutions, ...locations])]
  return values.length ? values.join(', ') : elementText(affiliation)
}

function parseAuthorsFromXml(xmlText) {
  if (!xmlText || typeof DOMParser === 'undefined') return []

  const document = new DOMParser().parseFromString(xmlText, 'application/xml')
  if (document.querySelector('parsererror')) return []

  const allElements = Array.from(document.getElementsByTagName('*'))
  const affiliationsById = new Map(
    allElements
      .filter((element) => element.localName === 'aff' && element.getAttribute('id'))
      .map((element) => [element.getAttribute('id'), element]),
  )
  const globalEmails = allElements
    .filter((element) => element.localName === 'email')
    .map(elementText)
    .filter(Boolean)
  const contribs = allElements.filter(
    (element) => element.localName === 'contrib' && element.getAttribute('contrib-type') === 'author',
  )

  return contribs.map((contrib, index) => {
    const givenNames = elementText(firstDescendant(contrib, 'given-names'))
    const surname = elementText(firstDescendant(contrib, 'surname'))
    const orcid = elementText(firstDescendant(contrib, 'contrib-id'))
    const directEmail = elementText(firstDescendant(contrib, 'email'))
    const inlineAffiliations = descendants(contrib, 'aff')
    const referencedAffiliations = descendants(contrib, 'xref')
      .filter((xref) => xref.getAttribute('ref-type') === 'aff')
      .map((xref) => affiliationsById.get(xref.getAttribute('rid')))
      .filter(Boolean)
    const affiliations = [...inlineAffiliations, ...referencedAffiliations]
    const affiliation = [...new Set(affiliations.map(formatAffiliation).filter(Boolean))].join('; ')

    const affiliationEmail = affiliations
      .map((aff) => elementText(firstDescendant(aff, 'email')))
      .find(Boolean) || ''
    const email = directEmail || affiliationEmail || globalEmails[index] || ''

    return {
      givenNames,
      surname,
      orcid,
      email,
      affiliation,
    }
  }).filter((author) => author.givenNames || author.surname || author.email || author.affiliation)
}

// Valores por defecto cuando el XML no contiene la etiqueta especificada
const defaultFrontValues = {
  journalTitle: 'Salud Colectiva',
  publisherName: 'Universidad Nacional de Lanús',
  issnPpub: '1414-9089',
  issnEpub: '1851-8265',
  doi: '10.18294/sc.2026.0000',
  publisherId: '0000',
  articleTitle: 'Sin título',
  subtitle: '',
  transTitleEn: '',
  abstractEs: '',
  abstractEn: '',
  pubDay: new Date().getDate().toString().padStart(2, '0'),
  pubMonth: (new Date().getMonth() + 1).toString().padStart(2, '0'),
  pubYear: new Date().getFullYear().toString(),
  licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  copyrightStatement: '© Universidad Nacional de Lanús',
  keywordsEs: [],
  keywordsEn: [],
  authors: [],
}

export default function App() {
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [xml, setXml] = useState('')
  const [imageCount, setImageCount] = useState(0)
  const [status, setStatus] = useState(null)
  const [loading, setLoading] = useState(false)
  const [downloadingByArticleId, setDownloadingByArticleId] = useState(false)

  // Estado del Formulario de Edición del <front>
  const [front, setFront] = useState(null)
  const [loadingFront, setLoadingFront] = useState(false)
  const [applyingFront, setApplyingFront] = useState(false)
  const [exportingXml, setExportingXml] = useState(false)

  const inputRef = useRef(null)

  const pickFile = useCallback((f) => {
    if (!f) return
    if (!f.name.toLowerCase().endsWith('.docx')) {
      setStatus({ type: 'error', message: 'El archivo debe ser .docx' })
      return
    }
    setFile(f)
    setXml('')
    setFront(null)
    setStatus(null)
  }, [])

  const onDrop = (e) => {
    e.preventDefault()
    setDragging(false)
    pickFile(e.dataTransfer.files?.[0])
  }

  const handlePreview = async () => {
    if (!file) return
    setLoading(true)
    setStatus(null)
    setFront(null)
    try {
      const form = new FormData()
      form.append('file', file)
      const res = await fetch(`${API_BASE}/api/convert/preview`, { method: 'POST', body: form })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || 'Error al convertir')
      setXml(data.xml)
      setImageCount(data.imageCount || 0)
      setStatus({ type: 'ok', message: `Conversión lista · ${data.imageCount} imagen(es) detectada(s)` })
      await loadFrontFields(data.xml, data.metadata)
    } catch (err) {
      setStatus({ type: 'error', message: err.message })
    } finally {
      setLoading(false)
    }
  }

  const loadFrontFields = async (xmlText, directMetadata = {}) => {
    setLoadingFront(true)
    try {
      let data = {}
      try {
        const res = await fetch(`${API_BASE}/api/convert/front-fields`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ xml: xmlText }),
        })
        if (res.ok) {
          data = await res.json()
        }
      } catch (e) {
        console.warn('Falló el endpoint front-fields, extrayendo datos directo del XML.', e)
      }

      const parsedFromXml = {
        journalTitle: getXmlTagValue(xmlText, 'journal-title'),
        publisherName: getXmlTagValue(xmlText, 'publisher-name'),
        articleTitle: getXmlTagValue(xmlText, 'article-title'),
        subtitle: getXmlTagValue(xmlText, 'subtitle'),
        transTitleEn: getXmlTagValue(xmlText, 'trans-title'),
        abstractEs: getXmlTagValue(xmlText, 'abstract'),
        abstractEn: getXmlTagValue(xmlText, 'trans-abstract'),
        copyrightStatement: getXmlTagValue(xmlText, 'copyright-statement'),
        authors: parseAuthorsFromXml(xmlText),
      }

      const parsedAuthors = parsedFromXml.authors || []
      const rawAuthors = (data.authors && data.authors.length)
        ? data.authors 
        : ((directMetadata?.authors && directMetadata.authors.length)
            ? directMetadata.authors 
            : parsedAuthors)

      const normalizedAuthors = (rawAuthors || []).map((author, index) => {
        const parsedAuthor = parsedAuthors[index] || {}
        const realEmail = author.email || parsedAuthor.email || ''
        return {
          ...parsedAuthor,
          ...author,
          affiliation: author.affiliation || parsedAuthor.affiliation || '',
          email: realEmail,
        }
      })

      const mergedFront = {
        journalTitle: data.journalTitle || directMetadata?.journalTitle || parsedFromXml.journalTitle || defaultFrontValues.journalTitle,
        publisherName: data.publisherName || directMetadata?.publisherName || parsedFromXml.publisherName || defaultFrontValues.publisherName,
        issnPpub: data.issnPpub || directMetadata?.issnPpub || defaultFrontValues.issnPpub,
        issnEpub: data.issnEpub || directMetadata?.issnEpub || defaultFrontValues.issnEpub,
        doi: data.doi || directMetadata?.doi || defaultFrontValues.doi,
        publisherId: data.publisherId || directMetadata?.publisherId || defaultFrontValues.publisherId,
        articleTitle: data.articleTitle || directMetadata?.articleTitle || parsedFromXml.articleTitle || defaultFrontValues.articleTitle,
        subtitle: data.subtitle || directMetadata?.subtitle || parsedFromXml.subtitle || defaultFrontValues.subtitle,
        transTitleEn: data.transTitleEn || directMetadata?.transTitleEn || parsedFromXml.transTitleEn || defaultFrontValues.transTitleEn,
        abstractEs: data.abstractEs || directMetadata?.abstractEs || parsedFromXml.abstractEs || defaultFrontValues.abstractEs,
        abstractEn: data.abstractEn || directMetadata?.abstractEn || parsedFromXml.abstractEn || defaultFrontValues.abstractEn,
        pubDay: data.pubDay || directMetadata?.pubDay || defaultFrontValues.pubDay,
        pubMonth: data.pubMonth || directMetadata?.pubMonth || defaultFrontValues.pubMonth,
        pubYear: data.pubYear || directMetadata?.pubYear || defaultFrontValues.pubYear,
        licenseUrl: data.licenseUrl || directMetadata?.licenseUrl || defaultFrontValues.licenseUrl,
        copyrightStatement: data.copyrightStatement || directMetadata?.copyrightStatement || parsedFromXml.copyrightStatement || defaultFrontValues.copyrightStatement,
        keywordsEs: (data.keywordsEs && data.keywordsEs.length) ? data.keywordsEs : (directMetadata?.keywordsEs || []),
        keywordsEn: (data.keywordsEn && data.keywordsEn.length) ? data.keywordsEn : (directMetadata?.keywordsEn || []),
        authors: normalizedAuthors,
      }

      setFront(mergedFront)
    } catch (err) {
      setStatus({ type: 'error', message: err.message })
    } finally {
      setLoadingFront(false)
    }
  }

  const updateFrontField = (key, value) => {
    setFront((prev) => ({ ...prev, [key]: value }))
  }

  const updateAuthorField = (index, key, value) => {
    setFront((prev) => {
      const authors = prev.authors.map((a, i) => {
        if (i === index) {
          return { ...a, [key]: value }
        }
        return a
      })
      return { ...prev, authors }
    })
  }

  const addAuthor = () => {
    setFront((prev) => ({
      ...prev,
      authors: [
        ...prev.authors,
        { givenNames: '', surname: '', orcid: '', email: '', affiliation: '' },
      ],
    }))
  }

  const removeAuthor = (index) => {
    setFront((prev) => ({
      ...prev,
      authors: prev.authors.filter((_, i) => i !== index),
    }))
  }

  const splitKeywords = (value) => {
    if (Array.isArray(value)) return value
    return (value || '')
      .split(',')
      .map((k) => k.trim())
      .filter(Boolean)
  }

  const handleApplyFront = async () => {
    if (!front || !xml) return
    setApplyingFront(true)
    setStatus(null)
    try {
      const payload = {
        ...front,
        keywordsEs: splitKeywords(front.keywordsEs),
        keywordsEn: splitKeywords(front.keywordsEn),
      }
      const res = await fetch(`${API_BASE}/api/convert/apply-front`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ xml, front: payload }),
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || 'No se pudo aplicar la edición')
      setXml(data.xml)
      if (data.valid) {
        setStatus({ type: 'ok', message: 'Cambios aplicados · el XML sigue siendo válido' })
      } else {
        setStatus({
          type: 'error',
          message: `Cambios aplicados con advertencias: ${data.validationErrors?.join('; ') || ''}`,
        })
      }
    } catch (err) {
      setStatus({ type: 'error', message: err.message })
    } finally {
      setApplyingFront(false)
    }
  }

  const handleExportEditedXml = async () => {
    if (!xml) return
    setExportingXml(true)
    setStatus(null)
    try {
      const baseName = stripExtension(file?.name)
      const res = await fetch(`${API_BASE}/api/convert/export-xml`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ xml, filename: baseName }),
      })
      if (!res.ok) {
        const data = await res.json()
        throw new Error(data.error || 'No se pudo exportar el XML')
      }
      const blob = await res.blob()
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `${baseName}-jats-editado.xml`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
      setStatus({ type: 'ok', message: 'XML editado descargado con éxito' })
    } catch (err) {
      setStatus({ type: 'error', message: err.message })
    } finally {
      setExportingXml(false)
    }
  }

  const handleDownload = async () => {
    if (!file) return
    const match = file.name.match(/\d+/)
    const extractedId = match ? match[0] : null

    if (!extractedId) {
      setStatus({
        type: 'error',
        message: 'No se pudo detectar el ID del artículo en el nombre del archivo.',
      })
      return
    }

    await getJatsXmlAxios(extractedId)
  }

  async function getJatsXmlAxios(idToFetch) {
    const id = idToFetch
    if (!id || !id.trim()) {
      setStatus({ type: 'error', message: 'No se pudo detectar el ID del artículo' })
      return
    }

    setDownloadingByArticleId(true)
    setStatus(null)
    window.history.pushState({}, '', `/jats/${id}`)

    try {
      const response = await axios.post(
        `${API_BASE}/jats`,
        { articleId: id },
        { responseType: 'blob' }
      )

      const blob = new Blob([response.data], { type: 'application/xml' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `article-${id}-jats.xml`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)

      setStatus({ type: 'ok', message: `JATS XML del artículo ${id} descargado con éxito` })
    } catch (error) {
      if (error.response && error.response.data) {
        const reader = new FileReader()
        reader.onload = () => {
          try {
            const errorJson = JSON.parse(reader.result)
            setStatus({ type: 'error', message: errorJson.error || 'Error procesando la solicitud' })
          } catch (e) {
            setStatus({ type: 'error', message: 'Error al procesar la respuesta del servidor' })
          }
        }
        reader.readAsText(error.response.data)
      } else {
        setStatus({ type: 'error', message: error.message || 'Error de conexión con el servidor' })
      }
    } finally {
      setDownloadingByArticleId(false)
    }
  }

  // Estilo común para envolver inputs dinámicos en flexbox
  const flexLabelStyle = {
    display: 'flex',
    flexDirection: 'column',
    gap: '4px',
    fontSize: '0.85rem',
    width: 'fit-content',
    maxWidth: '100%',
  }

  const rowWrapStyle = {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '16px',
    alignItems: 'flex-start',
    marginBottom: '12px',
  }

  return (
    <div className="container">
      <header className="masthead">
        <div className="masthead-content">
          <div className="eyebrow">Herramienta de conversión editorial</div>
          <h1>
            DOCX <span className="tag">→</span> JATS <span className="tag">&lt;xml/&gt;</span>
          </h1>
          <p>
            Subí un manuscrito en Word y obtené su equivalente en JATS (Journal Article Tag Suite):
            secciones anidadas, listas, tablas e imágenes convertidas a marcado XML listo para
            sistemas editoriales.
          </p>
        </div>
        <div className="unla-logo-container">
          <img src={logoUnla} alt="Universidad Nacional de Lanús" className="unla-logo" />
        </div>
      </header>

      <main className="workspace">
        {/* BLOQUE 01 · CARGA DE ARCHIVO */}
        <div className="sheet" data-label="01 · MANUSCRITO">
          <div
            className={`dropzone ${dragging ? 'dragging' : ''}`}
            onDragOver={(e) => {
              e.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
            onClick={() => inputRef.current?.click()}
          >
            <input
              ref={inputRef}
              type="file"
              accept=".docx"
              onChange={(e) => pickFile(e.target.files?.[0])}
            />

            <div className="dropzone-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
                <path d="M12 18v-6" />
                <path d="M9 15l3-3 3 3" />
              </svg>
            </div>

            <p className="hint">Arrastrá tu archivo .docx acá, o hacé click para elegirlo.</p>

            <div className="file-picker-row" onClick={(e) => e.stopPropagation()}>
              <button className="select-btn" onClick={() => inputRef.current?.click()}>
                Seleccionar archivo
              </button>
              <span className="filename-display">
                {file ? file.name : 'Ningún archivo seleccionado'}
              </span>
            </div>
          </div>

          <div className="actions">
            <button className="primary" onClick={handlePreview} disabled={!file || loading}>
              {loading ? 'Convirtiendo…' : 'CONVERTIR Y PREVISUALIZAR'}
            </button>
            <button className="secondary" onClick={handleDownload} disabled={!file || downloadingByArticleId}>
              {downloadingByArticleId ? 'Generando .xml…' : 'DESCARGAR XML JATS (.XML)'}
            </button>
          </div>
        </div>

        {status && (
          <div className={`status ${status.type}`}>
            {status.type === 'error' ? '✕ ' : '✓ '}{status.message}
          </div>
        )}

        {loadingFront && <div className="status">Cargando y parseando metadatos del XML…</div>}

        {/* BLOQUE 02 · EDICIÓN COMPLETA DEL FRONT-MATTER */}
        {front && (
          <div className="sheet" data-label="02 · EDICIÓN COMPLETA DEL METADATO (FRONT)">
            <div className="front-form">
              
              {/* DATOS DE LA REVISTA */}
              <div className="form-section-title">Datos de la Revista (&lt;journal-meta&gt;)</div>
              <div style={rowWrapStyle}>
                <label style={flexLabelStyle}>
                  Título de la Revista (&lt;journal-title&gt;)
                  <AutoInput
                    value={front.journalTitle || ''}
                    placeholder="Título de la revista"
                    minChars={20}
                    onChange={(e) => updateFrontField('journalTitle', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  Editorial / Institución (&lt;publisher-name&gt;)
                  <AutoInput
                    value={front.publisherName || ''}
                    placeholder="Nombre de la editorial"
                    minChars={25}
                    onChange={(e) => updateFrontField('publisherName', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  ISSN Impreso (&lt;issn pub-type="ppub"&gt;)
                  <AutoInput
                    value={front.issnPpub || ''}
                    placeholder="0000-0000"
                    minChars={12}
                    onChange={(e) => updateFrontField('issnPpub', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  ISSN Digital (&lt;issn pub-type="epub"&gt;)
                  <AutoInput
                    value={front.issnEpub || ''}
                    placeholder="0000-0000"
                    minChars={12}
                    onChange={(e) => updateFrontField('issnEpub', e.target.value)}
                  />
                </label>
              </div>

              {/* IDENTIFICADORES Y FECHAS */}
              <div className="form-section-title" style={{ marginTop: '20px' }}>
                Identificadores y Fechas (&lt;article-id&gt; / &lt;pub-date&gt;)
              </div>
              <div style={rowWrapStyle}>
                <label style={flexLabelStyle}>
                  DOI del Artículo (&lt;article-id pub-id-type="doi"&gt;)
                  <AutoInput
                    value={front.doi || ''}
                    placeholder="10.xxxx/xxxx"
                    minChars={22}
                    onChange={(e) => updateFrontField('doi', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  ID Interno (&lt;article-id pub-id-type="publisher-id"&gt;)
                  <AutoInput
                    value={front.publisherId || ''}
                    placeholder="ID"
                    minChars={8}
                    onChange={(e) => updateFrontField('publisherId', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  Día
                  <AutoInput
                    type="number"
                    placeholder="DD"
                    value={front.pubDay || ''}
                    minChars={4}
                    onChange={(e) => updateFrontField('pubDay', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  Mes
                  <AutoInput
                    type="number"
                    placeholder="MM"
                    value={front.pubMonth || ''}
                    minChars={4}
                    onChange={(e) => updateFrontField('pubMonth', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  Año
                  <AutoInput
                    type="number"
                    placeholder="YYYY"
                    value={front.pubYear || ''}
                    minChars={6}
                    onChange={(e) => updateFrontField('pubYear', e.target.value)}
                  />
                </label>
              </div>

              {/* TÍTULOS Y RESÚMENES */}
              <div className="form-section-title" style={{ marginTop: '20px' }}>
                Títulos y Resúmenes (&lt;title-group&gt; / &lt;abstract&gt;)
              </div>
              <div style={rowWrapStyle}>
                <label style={flexLabelStyle}>
                  Título Principal (&lt;article-title&gt;)
                  <AutoInput
                    value={front.articleTitle || ''}
                    placeholder="Título principal del artículo"
                    minChars={30}
                    onChange={(e) => updateFrontField('articleTitle', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  Subtítulo (&lt;subtitle&gt;)
                  <AutoInput
                    value={front.subtitle || ''}
                    placeholder="Subtítulo opcional"
                    minChars={20}
                    onChange={(e) => updateFrontField('subtitle', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  Título Traducido al Inglés (&lt;trans-title xml:lang="en"&gt;)
                  <AutoInput
                    value={front.transTitleEn || ''}
                    placeholder="Translated title"
                    minChars={30}
                    onChange={(e) => updateFrontField('transTitleEn', e.target.value)}
                  />
                </label>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginBottom: '12px' }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.85rem' }}>
                  Resumen Español (&lt;abstract xml:lang="es"&gt;)
                  <textarea
                    rows={4}
                    value={front.abstractEs || ''}
                    onChange={(e) => updateFrontField('abstractEs', e.target.value)}
                    style={{ width: '100%' }}
                  />
                </label>

                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.85rem' }}>
                  Abstract Inglés (&lt;trans-abstract xml:lang="en"&gt;)
                  <textarea
                    rows={4}
                    value={front.abstractEn || ''}
                    onChange={(e) => updateFrontField('abstractEn', e.target.value)}
                    style={{ width: '100%' }}
                  />
                </label>
              </div>

              {/* PALABRAS CLAVE */}
              <div className="form-section-title" style={{ marginTop: '20px' }}>
                Palabras Clave (&lt;kwd-group&gt;)
              </div>
              <div style={rowWrapStyle}>
                <label style={flexLabelStyle}>
                  Palabras clave (es) · separadas por coma
                  <AutoInput
                    value={Array.isArray(front.keywordsEs) ? front.keywordsEs.join(', ') : (front.keywordsEs || '')}
                    placeholder="palabra 1, palabra 2"
                    minChars={25}
                    onChange={(e) => updateFrontField('keywordsEs', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  Palabras clave (en) · separadas por coma
                  <AutoInput
                    value={Array.isArray(front.keywordsEn) ? front.keywordsEn.join(', ') : (front.keywordsEn || '')}
                    placeholder="keyword 1, keyword 2"
                    minChars={25}
                    onChange={(e) => updateFrontField('keywordsEn', e.target.value)}
                  />
                </label>
              </div>

              {/* LICENCIA Y DERECHOS */}
              <div className="form-section-title" style={{ marginTop: '20px' }}>
                Permisos y Licencia (&lt;permissions&gt;)
              </div>
              <div style={rowWrapStyle}>
                <label style={flexLabelStyle}>
                  URL de la Licencia Creative Commons (&lt;ali:license_ref&gt;)
                  <AutoInput
                    placeholder="https://creativecommons.org/licenses/by/4.0/"
                    value={front.licenseUrl || ''}
                    minChars={35}
                    onChange={(e) => updateFrontField('licenseUrl', e.target.value)}
                  />
                </label>
                <label style={flexLabelStyle}>
                  Declaración de Copyright (&lt;copyright-statement&gt;)
                  <AutoInput
                    placeholder="© 2026 Universidad Nacional de Lanús"
                    value={front.copyrightStatement || ''}
                    minChars={30}
                    onChange={(e) => updateFrontField('copyrightStatement', e.target.value)}
                  />
                </label>
              </div>

              {/* BLOQUE DE AUTORES */}
              <div className="authors-edit" style={{ marginTop: '20px' }}>
                <div className="authors-edit-title" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                  <span>Autores (&lt;contrib-group&gt;)</span>
                  <button type="button" className="select-btn" onClick={addAuthor} style={{ padding: '4px 10px', fontSize: '0.8rem' }}>
                    + Añadir Autor
                  </button>
                </div>

                {front.authors && front.authors.map((author, i) => (
                  <div key={i} style={{ border: '1px solid var(--border-color, #d0d0d0)', padding: '16px', borderRadius: '6px', marginBottom: '14px', backgroundColor: '#fff' }}>
                    
                    {/* Rejilla de campos de autor adaptables */}
                    <div style={rowWrapStyle}>
                      <label style={flexLabelStyle}>
                        Nombre/s
                        <AutoInput
                          placeholder="Nombre/s"
                          value={author.givenNames || ''}
                          minChars={14}
                          onChange={(e) => updateAuthorField(i, 'givenNames', e.target.value)}
                        />
                      </label>
                      <label style={flexLabelStyle}>
                        Apellido
                        <AutoInput
                          placeholder="Apellido"
                          value={author.surname || ''}
                          minChars={14}
                          onChange={(e) => updateAuthorField(i, 'surname', e.target.value)}
                        />
                      </label>
                      <label style={flexLabelStyle}>
                        ORCID
                        <AutoInput
                          placeholder="0000-0000-0000-0000"
                          value={author.orcid || ''}
                          minChars={20}
                          onChange={(e) => updateAuthorField(i, 'orcid', e.target.value)}
                        />
                      </label>
                      <label style={flexLabelStyle}>
                        Correo electrónico 
                        <AutoInput
                          type="email"
                          placeholder="correo@ejemplo.com"
                          value={author.email || ''}
                          minChars={20}
                          onChange={(e) => updateAuthorField(i, 'email', e.target.value)}
                        />
                      </label>
                    </div>

                    {/* Campo de Afiliación */}
                    <div style={{ marginBottom: '12px' }}>
                      <label style={flexLabelStyle}>
                        Afiliación Institucional (&lt;aff&gt; / &lt;institution&gt;)
                        <AutoInput
                          placeholder="Ej: Universidad Nacional de Lanús, Lanús, Argentina"
                          value={author.affiliation || ''}
                          minChars={40}
                          onChange={(e) => updateAuthorField(i, 'affiliation', e.target.value)}
                        />
                      </label>
                    </div>

                    {/* Pie del bloque de autor */}
                    <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: '8px', borderTop: '1px solid #f0f0f0' }}>
                      <button
                        type="button"
                        onClick={() => removeAuthor(i)}
                        style={{ color: '#d9534f', background: 'none', border: 'none', cursor: 'pointer', marginLeft: 'auto', fontWeight: 'bold' }}
                      >
                        Eliminar
                      </button>
                    </div>

                  </div>
                ))}
              </div>

            </div>

            <div className="actions" style={{ marginTop: '20px' }}>
              <button className="primary" onClick={handleApplyFront} disabled={applyingFront}>
                {applyingFront ? 'Aplicando…' : 'APLICAR CAMBIOS AL XML'}
              </button>
              <button className="secondary" onClick={handleExportEditedXml} disabled={exportingXml || !xml}>
                {exportingXml ? 'Generando…' : 'DESCARGAR XML EDITADO (.XML)'}
              </button>
            </div>
          </div>
        )}

        {/* BLOQUE 03 · PREVISUALIZACIÓN */}
        {xml && (
          <div className="sheet" data-label={`03 · ARTICLE.XML${imageCount ? ` · ${imageCount} IMAGEN(ES)` : ''}`}>
            <pre className="xml-view" dangerouslySetInnerHTML={{ __html: highlightXml(xml) }} />
          </div>
        )}
      </main>

      <footer className="credit">spring boot · apache poi · react — conversor docx→jats</footer>
    </div>
  )
}