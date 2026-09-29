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
  try {
    const document = new DOMParser().parseFromString(xml, 'application/xml')
    const node = Array.from(document.getElementsByTagName('*')).find((element) => element.localName === tagName)
    return node?.textContent?.trim() || ''
  } catch (e) {
    return ''
  }
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

  try {
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
  } catch (e) {
    return []
  }
}

// Licencias Creative Commons predefinidas
const CC_LICENSES = {
  'by-4.0': {
    name: 'CC BY 4.0 (Atribución)',
    url: 'https://creativecommons.org/licenses/by/4.0/',
    text: 'Este es un artículo publicado en acceso abierto bajo una licencia Creative Commons Atribución 4.0 Internacional (CC BY 4.0)',
  },
  'by-nc-4.0': {
    name: 'CC BY-NC 4.0 (No Comercial)',
    url: 'https://creativecommons.org/licenses/by-nc/4.0/',
    text: 'Este es un artículo publicado en acceso abierto bajo una licencia Creative Commons No Comercial (CC BY-NC 4.0)',
  },
  'by-nc-nd-4.0': {
    name: 'CC BY-NC-ND 4.0 (No Comercial - Sin Derivadas)',
    url: 'https://creativecommons.org/licenses/by-nc-nd/4.0/',
    text: 'Este es un artículo publicado en acceso abierto bajo una licencia Creative Commons No Comercial - Sin Obra Derivada (CC BY-NC-ND 4.0)',
  },
  'by-sa-4.0': {
    name: 'CC BY-SA 4.0 (Compartir Igual)',
    url: 'https://creativecommons.org/licenses/by-sa/4.0/',
    text: 'Este es un artículo publicado en acceso abierto bajo una licencia Creative Commons Compartir Igual (CC BY-SA 4.0)',
  },
}

// Perfil estándar de Salud Colectiva (UNLa)
const PRESET_SALUD_COLECTIVA = {
  articleType: 'research-article',
  dtdVersion: '1.1',
  spsVersion: 'sps-1.9',
  xmlLang: 'es',
  journalTitle: 'Salud Colectiva',
  journalAbbrev: 'Salud Colect',
  journalIdNlmTa: 'Salud Colect',
  journalIdPublisher: 'scol',
  issnPpub: '1669-2381',
  issnEpub: '1851-8265',
  publisherName: 'Universidad Nacional de Lanús',
  doi: '10.18294/sc.2026.6101',
  publisherId: '6101',
  subject: 'Artículos',
  volume: '22',
  issue: '',
  elocationId: 'e6101',
  pubDay: '09',
  pubMonth: '09',
  pubYear: '2026',
  collectionYear: '2026',
  historyReceivedDay: '15',
  historyReceivedMonth: '02',
  historyReceivedYear: '2026',
  historyAcceptedDay: '16',
  historyAcceptedMonth: '06',
  historyAcceptedYear: '2026',
  historyPubDay: '09',
  historyPubMonth: '09',
  historyPubYear: '2026',
  licenseType: 'open-access',
  licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  licenseText: 'Este es un artículo publicado en acceso abierto bajo una licencia Creative Commons',
  copyrightStatement: '© 2026 Universidad Nacional de Lanús',
}

// Perfil genérico multirevista
const PRESET_GENERICO = {
  articleType: 'research-article',
  dtdVersion: '1.1',
  spsVersion: 'sps-1.9',
  xmlLang: 'es',
  journalTitle: 'Nombre de la Revista',
  journalAbbrev: 'Nom Rev',
  journalIdNlmTa: 'Nom Rev',
  journalIdPublisher: 'nrev',
  issnPpub: '0000-0000',
  issnEpub: '0000-0000',
  publisherName: 'Editorial Universitaria',
  doi: '10.xxxx/xxxx',
  publisherId: '0001',
  subject: 'Artículos',
  volume: '1',
  issue: '1',
  elocationId: 'e0001',
  pubDay: '01',
  pubMonth: '01',
  pubYear: new Date().getFullYear().toString(),
  collectionYear: new Date().getFullYear().toString(),
  historyReceivedDay: '01',
  historyReceivedMonth: '01',
  historyReceivedYear: new Date().getFullYear().toString(),
  historyAcceptedDay: '01',
  historyAcceptedMonth: '06',
  historyAcceptedYear: new Date().getFullYear().toString(),
  historyPubDay: '01',
  historyPubMonth: '07',
  historyPubYear: new Date().getFullYear().toString(),
  licenseType: 'open-access',
  licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
  licenseText: 'Este es un artículo publicado en acceso abierto bajo una licencia Creative Commons',
  copyrightStatement: `© ${new Date().getFullYear()} Editorial Universitaria`,
}

// Valores por defecto iniciales
const defaultFrontValues = {
  ...PRESET_SALUD_COLECTIVA,
  articleTitle: 'Sin título',
  subtitle: '',
  transTitleEn: '',
  abstractEs: '',
  abstractEn: '',
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

  // Estado del Formulario de Edición del <front> (4 Bloques)
  const [front, setFront] = useState(null)
  const [loadingFront, setLoadingFront] = useState(false)
  const [applyingFront, setApplyingFront] = useState(false)
  const [exportingXml, setExportingXml] = useState(false)
  const [copied, setCopied] = useState(false)

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
        // Bloque 1
        articleType: data.articleType || defaultFrontValues.articleType,
        // Bloque 2
        dtdVersion: data.dtdVersion || defaultFrontValues.dtdVersion,
        spsVersion: data.spsVersion || defaultFrontValues.spsVersion,
        xmlLang: data.xmlLang || defaultFrontValues.xmlLang,
        // Bloque 3
        journalTitle: data.journalTitle || directMetadata?.journalTitle || parsedFromXml.journalTitle || defaultFrontValues.journalTitle,
        journalAbbrev: data.journalAbbrev || defaultFrontValues.journalAbbrev,
        journalIdNlmTa: data.journalIdNlmTa || defaultFrontValues.journalIdNlmTa,
        journalIdPublisher: data.journalIdPublisher || defaultFrontValues.journalIdPublisher,
        publisherName: data.publisherName || directMetadata?.publisherName || parsedFromXml.publisherName || defaultFrontValues.publisherName,
        issnPpub: data.issnPpub || directMetadata?.issnPpub || defaultFrontValues.issnPpub,
        issnEpub: data.issnEpub || directMetadata?.issnEpub || defaultFrontValues.issnEpub,
        doi: data.doi || directMetadata?.doi || defaultFrontValues.doi,
        publisherId: data.publisherId || directMetadata?.publisherId || defaultFrontValues.publisherId,
        subject: data.subject || defaultFrontValues.subject,
        // Bloque 4
        pubDay: data.pubDay || directMetadata?.pubDay || defaultFrontValues.pubDay,
        pubMonth: data.pubMonth || directMetadata?.pubMonth || defaultFrontValues.pubMonth,
        pubYear: data.pubYear || directMetadata?.pubYear || defaultFrontValues.pubYear,
        collectionYear: data.collectionYear || defaultFrontValues.collectionYear,
        volume: data.volume || defaultFrontValues.volume,
        issue: data.issue || defaultFrontValues.issue,
        elocationId: data.elocationId || defaultFrontValues.elocationId,
        historyReceivedDay: data.historyReceivedDay || defaultFrontValues.historyReceivedDay,
        historyReceivedMonth: data.historyReceivedMonth || defaultFrontValues.historyReceivedMonth,
        historyReceivedYear: data.historyReceivedYear || defaultFrontValues.historyReceivedYear,
        historyAcceptedDay: data.historyAcceptedDay || defaultFrontValues.historyAcceptedDay,
        historyAcceptedMonth: data.historyAcceptedMonth || defaultFrontValues.historyAcceptedMonth,
        historyAcceptedYear: data.historyAcceptedYear || defaultFrontValues.historyAcceptedYear,
        historyPubDay: data.historyPubDay || defaultFrontValues.historyPubDay,
        historyPubMonth: data.historyPubMonth || defaultFrontValues.historyPubMonth,
        historyPubYear: data.historyPubYear || defaultFrontValues.historyPubYear,
        licenseType: data.licenseType || defaultFrontValues.licenseType,
        licenseUrl: data.licenseUrl || directMetadata?.licenseUrl || defaultFrontValues.licenseUrl,
        licenseText: data.licenseText || defaultFrontValues.licenseText,
        copyrightStatement: data.copyrightStatement || directMetadata?.copyrightStatement || parsedFromXml.copyrightStatement || defaultFrontValues.copyrightStatement,
        // Bloque 5
        articleTitle: data.articleTitle || directMetadata?.articleTitle || parsedFromXml.articleTitle || defaultFrontValues.articleTitle,
        subtitle: data.subtitle || directMetadata?.subtitle || parsedFromXml.subtitle || defaultFrontValues.subtitle,
        transTitleEn: data.transTitle || directMetadata?.transTitleEn || parsedFromXml.transTitleEn || defaultFrontValues.transTitleEn,
        abstractEs: data.abstractText || directMetadata?.abstractEs || parsedFromXml.abstractEs || defaultFrontValues.abstractEs,
        abstractEn: data.transAbstractText || directMetadata?.abstractEn || parsedFromXml.abstractEn || defaultFrontValues.abstractEn,
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

  const applyPreset = (presetKey) => {
    const preset = presetKey === 'salud-colectiva' ? PRESET_SALUD_COLECTIVA : PRESET_GENERICO
    setFront((prev) => ({
      ...prev,
      ...preset,
    }))
    setStatus({ type: 'ok', message: `Plantilla aplicada: ${presetKey === 'salud-colectiva' ? 'Salud Colectiva (UNLa)' : 'Revista Científica Genérica'}` })
  }

  const handleLicensePresetChange = (key) => {
    const lic = CC_LICENSES[key]
    if (lic) {
      setFront((prev) => ({
        ...prev,
        licenseUrl: lic.url,
        licenseText: lic.text,
      }))
    }
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
        transTitle: front.transTitleEn || front.transTitle,
        abstractText: front.abstractEs || front.abstractText,
        transAbstractText: front.abstractEn || front.transAbstractText,
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
        setStatus({ type: 'ok', message: 'Cambios aplicados con éxito · El XML cumple con la estructura requerida' })
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

  const handleCopyXml = async () => {
    if (!xml) return
    try {
      await navigator.clipboard.writeText(xml)
      setCopied(true)
      setTimeout(() => setCopied(false), 2500)
    } catch (e) {
      setStatus({ type: 'error', message: 'No se pudo copiar el XML al portapapeles' })
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
            Conversión y marcado automatizado de manuscritos científicos a JATS XML conforme
            al SciELO Publishing Schema (SPS 1.9 / 1.10). Soporte integral para los 4 bloques
            de publicación multirevista y validación formal de estándares.
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

        {/* BLOQUE 02 · EDICIÓN COMPLETA DEL METADATO (4 BLOQUES MULTIREVISTA) */}
        {front && (
          <div className="sheet" data-label="02 · CONFIGURACIÓN Y METADATOS EDITORIALES (4 BLOQUES)">
            
            {/* Barra de Plantillas Rápidas */}
            <div className="preset-bar">
              <span className="preset-label">Plantillas Rápidas:</span>
              <button
                type="button"
                className={`preset-btn ${front.journalTitle === 'Salud Colectiva' ? 'active' : ''}`}
                onClick={() => applyPreset('salud-colectiva')}
              >
                Salud Colectiva (UNLa)
              </button>
              <button
                type="button"
                className={`preset-btn ${front.journalTitle !== 'Salud Colectiva' ? 'active' : ''}`}
                onClick={() => applyPreset('generico')}
              >
                Revista Genérica / Multirevista
              </button>
            </div>

            <div className="front-form">
              
              {/* BLOQUE 1 · TIPO DE ARTÍCULO */}
              <div className="block-card">
                <div className="block-header">
                  <span className="block-tag">Bloque 1 · Tipo de Artículo</span>
                  <a
                    href="https://jats.nlm.nih.gov/nlm-dtd/articleauthoring/tag-library/2.1/n-24v0.html"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block-link"
                  >
                    Tag Library NLM (article-type) ↗
                  </a>
                </div>
                <div style={rowWrapStyle}>
                  <label style={flexLabelStyle}>
                    Tipo de Artículo (&lt;article article-type="..."&gt;)
                    <select
                      className="form-select"
                      value={front.articleType || 'research-article'}
                      onChange={(e) => updateFrontField('articleType', e.target.value)}
                    >
                      <option value="research-article">research-article (Artículo de investigación)</option>
                      <option value="review-article">review-article (Artículo de revisión / meta-análisis)</option>
                      <option value="editorial">editorial (Editorial / Declaración institucional)</option>
                      <option value="letter">letter (Carta al editor / debate)</option>
                      <option value="case-report">case-report (Reporte o estudio de caso)</option>
                      <option value="brief-report">brief-report (Comunicación breve / reporte preliminar)</option>
                      <option value="book-review">book-review (Reseña de libro)</option>
                      <option value="other">other (Otro tipo de contribución)</option>
                    </select>
                  </label>
                </div>
              </div>

              {/* BLOQUE 2 · ESTÁNDARES RAÍZ */}
              <div className="block-card">
                <div className="block-header">
                  <span className="block-tag">Bloque 2 · Estándares Raíz (DTD & SPS)</span>
                  <span className="muted" style={{ fontSize: '12px' }}>Parámetros globales del XML</span>
                </div>
                <div style={rowWrapStyle}>
                  <label style={flexLabelStyle}>
                    Versión DTD JATS (dtd-version)
                    <select
                      className="form-select"
                      value={front.dtdVersion || '1.1'}
                      onChange={(e) => updateFrontField('dtdVersion', e.target.value)}
                    >
                      <option value="1.1">1.1 (Recomendado SciELO / NISO 2015)</option>
                      <option value="1.2">1.2 (JATS 1.2)</option>
                      <option value="1.3">1.3 (JATS 1.3)</option>
                    </select>
                  </label>

                  <label style={flexLabelStyle}>
                    SciELO Publishing Schema (specific-use)
                    <select
                      className="form-select"
                      value={front.spsVersion || 'sps-1.9'}
                      onChange={(e) => updateFrontField('spsVersion', e.target.value)}
                    >
                      <option value="sps-1.9">sps-1.9 (Vigente SciELO)</option>
                      <option value="sps-1.10">sps-1.10 (Nueva especificación)</option>
                    </select>
                  </label>

                  <label style={flexLabelStyle}>
                    Idioma Principal (xml:lang)
                    <select
                      className="form-select"
                      value={front.xmlLang || 'es'}
                      onChange={(e) => updateFrontField('xmlLang', e.target.value)}
                    >
                      <option value="es">es (Español)</option>
                      <option value="en">en (English)</option>
                      <option value="pt">pt (Português)</option>
                    </select>
                  </label>
                </div>
              </div>

              {/* BLOQUE 3 · DATOS DE REVISTA, EDITORIAL, DOI Y SECCIÓN */}
              <div className="block-card">
                <div className="block-header">
                  <span className="block-tag">Bloque 3 · Revista, Publicador, DOI & Sección</span>
                  <span className="muted" style={{ fontSize: '12px' }}>&lt;journal-meta&gt; & &lt;article-id&gt;</span>
                </div>
                <div style={rowWrapStyle}>
                  <label style={flexLabelStyle}>
                    Título de la Revista (&lt;journal-title&gt;)
                    <AutoInput
                      value={front.journalTitle || ''}
                      placeholder="Título de la revista"
                      minChars={22}
                      onChange={(e) => updateFrontField('journalTitle', e.target.value)}
                    />
                  </label>

                  <label style={flexLabelStyle}>
                    Título Abreviado (&lt;abbrev-journal-title&gt;)
                    <AutoInput
                      value={front.journalAbbrev || ''}
                      placeholder="Abrev. oficial"
                      minChars={14}
                      onChange={(e) => updateFrontField('journalAbbrev', e.target.value)}
                    />
                  </label>

                  <label style={flexLabelStyle}>
                    ID NLM-TA (&lt;journal-id journal-id-type="nlm-ta"&gt;)
                    <AutoInput
                      value={front.journalIdNlmTa || ''}
                      placeholder="NLM-TA ID"
                      minChars={12}
                      onChange={(e) => updateFrontField('journalIdNlmTa', e.target.value)}
                    />
                  </label>

                  <label style={flexLabelStyle}>
                    ID Editorial (&lt;journal-id journal-id-type="publisher-id"&gt;)
                    <AutoInput
                      value={front.journalIdPublisher || ''}
                      placeholder="scol"
                      minChars={10}
                      onChange={(e) => updateFrontField('journalIdPublisher', e.target.value)}
                    />
                  </label>
                </div>

                <div style={rowWrapStyle}>
                  <label style={flexLabelStyle}>
                    Editorial / Institución (&lt;publisher-name&gt;)
                    <AutoInput
                      value={front.publisherName || ''}
                      placeholder="Nombre de la institución editorial"
                      minChars={28}
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

                  <label style={flexLabelStyle}>
                    DOI del Artículo (&lt;article-id pub-id-type="doi"&gt;)
                    <AutoInput
                      value={front.doi || ''}
                      placeholder="10.18294/sc.2026.xxxx"
                      minChars={24}
                      onChange={(e) => updateFrontField('doi', e.target.value)}
                    />
                  </label>

                  <label style={flexLabelStyle}>
                    Sección Temática (&lt;subject&gt;)
                    <AutoInput
                      value={front.subject || ''}
                      placeholder="Artículos / Editorial"
                      minChars={14}
                      onChange={(e) => updateFrontField('subject', e.target.value)}
                    />
                  </label>
                </div>
              </div>

              {/* BLOQUE 4 · FECHAS, NUMERACIÓN, HISTORIAL Y LICENCIAS */}
              <div className="block-card">
                <div className="block-header">
                  <span className="block-tag">Bloque 4 · Fechas, Numeración, Historial & Licencias</span>
                  <a
                    href="https://creativecommons.org/cc-licenses/"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block-link"
                  >
                    Guía de Licencias Creative Commons ↗
                  </a>
                </div>

                <div style={rowWrapStyle}>
                  <label style={flexLabelStyle}>
                    Fecha Publicación Electrónica (Día / Mes / Año)
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <AutoInput
                        type="number"
                        placeholder="DD"
                        value={front.pubDay || ''}
                        minChars={4}
                        onChange={(e) => updateFrontField('pubDay', e.target.value)}
                      />
                      <AutoInput
                        type="number"
                        placeholder="MM"
                        value={front.pubMonth || ''}
                        minChars={4}
                        onChange={(e) => updateFrontField('pubMonth', e.target.value)}
                      />
                      <AutoInput
                        type="number"
                        placeholder="AAAA"
                        value={front.pubYear || ''}
                        minChars={6}
                        onChange={(e) => updateFrontField('pubYear', e.target.value)}
                      />
                    </div>
                  </label>

                  <label style={flexLabelStyle}>
                    Año Colección (&lt;pub-date date-type="collection"&gt;)
                    <AutoInput
                      type="number"
                      placeholder="AAAA"
                      value={front.collectionYear || ''}
                      minChars={6}
                      onChange={(e) => updateFrontField('collectionYear', e.target.value)}
                    />
                  </label>

                  <label style={flexLabelStyle}>
                    Volumen (&lt;volume&gt;)
                    <AutoInput
                      placeholder="22"
                      value={front.volume || ''}
                      minChars={6}
                      onChange={(e) => updateFrontField('volume', e.target.value)}
                    />
                  </label>

                  <label style={flexLabelStyle}>
                    Número (&lt;issue&gt;)
                    <AutoInput
                      placeholder="1"
                      value={front.issue || ''}
                      minChars={6}
                      onChange={(e) => updateFrontField('issue', e.target.value)}
                    />
                  </label>

                  <label style={flexLabelStyle}>
                    Elocation-ID (&lt;elocation-id&gt;)
                    <AutoInput
                      placeholder="e6101"
                      value={front.elocationId || ''}
                      minChars={8}
                      onChange={(e) => updateFrontField('elocationId', e.target.value)}
                    />
                  </label>
                </div>

                {/* Subsección Historial Editorial */}
                <div style={{ margin: '14px 0 10px', fontSize: '12px', fontWeight: '600', color: 'var(--ink-soft)', textTransform: 'uppercase' }}>
                  Historial del Ciclo Editorial (&lt;history&gt;)
                </div>
                <div style={rowWrapStyle}>
                  <label style={flexLabelStyle}>
                    Fecha Recepción (DD/MM/AAAA)
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <AutoInput
                        type="number"
                        placeholder="DD"
                        value={front.historyReceivedDay || ''}
                        minChars={4}
                        onChange={(e) => updateFrontField('historyReceivedDay', e.target.value)}
                      />
                      <AutoInput
                        type="number"
                        placeholder="MM"
                        value={front.historyReceivedMonth || ''}
                        minChars={4}
                        onChange={(e) => updateFrontField('historyReceivedMonth', e.target.value)}
                      />
                      <AutoInput
                        type="number"
                        placeholder="AAAA"
                        value={front.historyReceivedYear || ''}
                        minChars={6}
                        onChange={(e) => updateFrontField('historyReceivedYear', e.target.value)}
                      />
                    </div>
                  </label>

                  <label style={flexLabelStyle}>
                    Fecha Aceptación (DD/MM/AAAA)
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <AutoInput
                        type="number"
                        placeholder="DD"
                        value={front.historyAcceptedDay || ''}
                        minChars={4}
                        onChange={(e) => updateFrontField('historyAcceptedDay', e.target.value)}
                      />
                      <AutoInput
                        type="number"
                        placeholder="MM"
                        value={front.historyAcceptedMonth || ''}
                        minChars={4}
                        onChange={(e) => updateFrontField('historyAcceptedMonth', e.target.value)}
                      />
                      <AutoInput
                        type="number"
                        placeholder="AAAA"
                        value={front.historyAcceptedYear || ''}
                        minChars={6}
                        onChange={(e) => updateFrontField('historyAcceptedYear', e.target.value)}
                      />
                    </div>
                  </label>

                  <label style={flexLabelStyle}>
                    Fecha Publicación (DD/MM/AAAA)
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <AutoInput
                        type="number"
                        placeholder="DD"
                        value={front.historyPubDay || ''}
                        minChars={4}
                        onChange={(e) => updateFrontField('historyPubDay', e.target.value)}
                      />
                      <AutoInput
                        type="number"
                        placeholder="MM"
                        value={front.historyPubMonth || ''}
                        minChars={4}
                        onChange={(e) => updateFrontField('historyPubMonth', e.target.value)}
                      />
                      <AutoInput
                        type="number"
                        placeholder="AAAA"
                        value={front.historyPubYear || ''}
                        minChars={6}
                        onChange={(e) => updateFrontField('historyPubYear', e.target.value)}
                      />
                    </div>
                  </label>
                </div>

                {/* Subsección Permisos y Licencias */}
                <div style={{ margin: '14px 0 10px', fontSize: '12px', fontWeight: '600', color: 'var(--ink-soft)', textTransform: 'uppercase' }}>
                  Permisos y Licencia de Acceso Abierto (&lt;permissions&gt;)
                </div>
                <div style={rowWrapStyle}>
                  <label style={flexLabelStyle}>
                    Plantilla Licencia Creative Commons
                    <select
                      className="form-select"
                      onChange={(e) => handleLicensePresetChange(e.target.value)}
                      defaultValue="by-4.0"
                    >
                      {Object.entries(CC_LICENSES).map(([k, lic]) => (
                        <option key={k} value={k}>{lic.name}</option>
                      ))}
                    </select>
                  </label>

                  <label style={flexLabelStyle}>
                    URL de la Licencia (&lt;license xlink:href="..."&gt;)
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
              </div>

              {/* BLOQUE 5 · CONTENIDO DEL MANUSCRITO & AUTORES */}
              <div className="block-card">
                <div className="block-header">
                  <span className="block-tag">Bloque 5 · Contenido del Manuscrito & Autores</span>
                  <span className="muted" style={{ fontSize: '12px' }}>Títulos, Resúmenes, Palabras Clave y Filiaciones</span>
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

                {/* Autores */}
                <div className="authors-edit" style={{ marginTop: '20px' }}>
                  <div className="authors-edit-title" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                    <strong>Autores (&lt;contrib-group&gt;)</strong>
                    <button type="button" className="select-btn" onClick={addAuthor} style={{ padding: '4px 10px', fontSize: '0.8rem' }}>
                      + Añadir Autor
                    </button>
                  </div>

                  {front.authors && front.authors.map((author, i) => (
                    <div key={i} style={{ border: '1px solid var(--border-color, #d0d0d0)', padding: '16px', borderRadius: '6px', marginBottom: '14px', backgroundColor: '#faf9f6' }}>
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

        {/* CENTRO DE CALIDAD Y STYLECHECKERS FORMALES */}
        {xml && (
          <div className="sheet quality-sheet" data-label="CALIDAD EDITORIAL & STYLECHECKERS">
            <div className="quality-header">
              <div className="quality-info">
                <h3>Verificación Formal de Calidad y Estándares</h3>
                <p>
                  Conforme a los criterios de <strong>SciELO</strong> y <strong>PubMed Central (PMC)</strong>,
                  la calidad y validez del marcado XML JATS no se mide por similitud del peso en bytes, sino por
                  la <strong>precisión semántica y la estricta conformidad del esquema DTD / Schematron</strong>.
                </p>
              </div>
            </div>

            <div className="stylechecker-actions">
              <a
                href="https://manager.scielo.org/tools/validators/stylechecker/"
                target="_blank"
                rel="noopener noreferrer"
                className="btn-validator scielo-btn"
              >
                <span className="validator-icon">✓</span>
                <span>Validar en <strong>SciELO Stylechecker</strong> ↗</span>
              </a>

              <a
                href="https://pmc.ncbi.nlm.nih.gov/tools/stylechecker/"
                target="_blank"
                rel="noopener noreferrer"
                className="btn-validator pmc-btn"
              >
                <span className="validator-icon">✓</span>
                <span>Validar en <strong>PMC Stylechecker</strong> ↗</span>
              </a>

              <button
                type="button"
                className="btn-validator copy-btn"
                onClick={handleCopyXml}
              >
                <span>{copied ? '✓ ¡XML Copiado al Portapapeles!' : '📋 Copiar XML al Portapapeles'}</span>
              </button>
            </div>
          </div>
        )}

        {/* BLOQUE 03 · PREVISUALIZACIÓN */}
        {xml && (
          <div className="sheet" data-label={`03 · PREVISUALIZACIÓN XML${imageCount ? ` · ${imageCount} IMAGEN(ES)` : ''}`}>
            <pre className="xml-view" dangerouslySetInnerHTML={{ __html: highlightXml(xml) }} />
          </div>
        )}
      </main>

      <footer className="credit">UNLa · spring boot · apache poi · react — conversor docx→jats</footer>
    </div>
  )
}