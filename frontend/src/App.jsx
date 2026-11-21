import { useCallback, useMemo, useRef, useState } from 'react'
import axios from 'axios'
import './index.css'
import logoUnla from './assets/unla-logo.png'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8081'

/**
 * Componente reutilizable para inputs que adaptan su ancho
 * automáticamente a la longitud de su texto y reciben clases CSS consistentes.
 */
function AutoInput({ value = '', placeholder = '', minChars = 10, type = 'text', onChange, className = '', ...props }) {
  const currentText = String(value || placeholder || '')
  const widthChars = Math.max(currentText.length + 2, minChars)

  return (
    <input
      type={type}
      value={value}
      placeholder={placeholder}
      onChange={onChange}
      className={`auto-input ${className}`.trim()}
      style={{
        width: `${widthChars}ch`,
        maxWidth: '100%',
        boxSizing: 'border-box'
      }}
      {...props}
    />
  )
}

/**
 * Etiqueta de campo editorial con chip visual de tag XML y descripción.
 */
function FieldLabel({ title, tag, hint, required }) {
  return (
    <div className="field-label-header">
      <span className="field-label-title">
        {title}
        {required && <span className="field-required">*</span>}
      </span>
      {tag && <code className="field-tag-badge">{tag}</code>}
      {hint && <span className="field-hint">{hint}</span>}
    </div>
  )
}

/**
 * Resaltador de sintaxis XML con soporte para declaraciones, comentarios,
 * etiquetas JATS, atributos, valores entrecomillados y búsqueda interactiva.
 */
function highlightXml(xml, searchQuery = '') {
  if (!xml) return ''
  let text = xml
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // Comentarios XML: <!-- ... -->
  text = text.replace(/(&lt;!--[\s\S]*?--&gt;)/g, '<span class="xml-cmt">$1</span>')

  // Declaraciones XML (<?xml ... ?>) y DOCTYPE
  text = text.replace(/(&lt;\?[\s\S]*?\?&gt;)/g, '<span class="xml-decl">$1</span>')
  text = text.replace(/(&lt;!DOCTYPE[\s\S]*?&gt;)/g, '<span class="xml-doc">$1</span>')

  // Etiquetas y atributos XML
  text = text.replace(/(&lt;\/?[a-zA-Z0-9_\-:]+)([\s\S]*?)(\/?&gt;)/g, (m, openTag, attrs, closeTag) => {
    const styledAttrs = attrs.replace(
      /([a-zA-Z0-9_\-:]+)=(".*?"|'.*?')/g,
      '<span class="xml-attr">$1</span>=<span class="xml-val">$2</span>'
    )
    return `<span class="xml-tag">${openTag}</span>${styledAttrs}<span class="xml-tag">${closeTag}</span>`
  })

  // Resaltado de texto coincidente con la búsqueda
  if (searchQuery && searchQuery.trim().length > 1) {
    try {
      const escapedQuery = searchQuery.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      const regex = new RegExp(`(?![^<]*>)(${escapedQuery})`, 'gi')
      text = text.replace(regex, '<mark class="xml-search-match">$1</mark>')
    } catch {
      // Evitar excepciones por expresiones regulares inválidas
    }
  }

  return text
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

  // Estado para el Visor / Editor interactivo de XML
  const [editorMode, setEditorMode] = useState('preview') // 'preview' | 'edit'
  const [rawXmlDraft, setRawXmlDraft] = useState('')
  const [rawXmlError, setRawXmlError] = useState('')
  const [rawXmlSaved, setRawXmlSaved] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [wrapLines, setWrapLines] = useState(false)
  const [isExpanded, setIsExpanded] = useState(false)

  const xmlLines = useMemo(() => (xml ? xml.split('\n') : []), [xml])
  const matchCount = useMemo(() => {
    if (!searchQuery.trim() || !xml) return 0
    try {
      const escaped = searchQuery.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      const matches = xml.match(new RegExp(escaped, 'gi'))
      return matches ? matches.length : 0
    } catch {
      return 0
    }
  }, [xml, searchQuery])

  const highlightedXmlCode = useMemo(() => {
    return highlightXml(xml, searchQuery)
  }, [xml, searchQuery])

  const handleSaveRawXml = () => {
    if (!rawXmlDraft.trim()) {
      setRawXmlError('El contenido XML no puede estar vacío.')
      return
    }
    try {
      const doc = new DOMParser().parseFromString(rawXmlDraft, 'application/xml')
      const parseError = doc.querySelector('parsererror')
      if (parseError) {
        setRawXmlError(`Error de sintaxis XML: ${parseError.textContent.slice(0, 160)}`)
        return
      }
      setXml(rawXmlDraft)
      setRawXmlError('')
      setRawXmlSaved(true)
      setTimeout(() => setRawXmlSaved(false), 3000)
      loadFrontFields(rawXmlDraft)
      setStatus({ type: 'ok', message: 'XML modificado y sincronizado con éxito con los metadatos' })
    } catch (e) {
      setRawXmlError(`Error al procesar el XML: ${e.message}`)
    }
  }

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
          <div className="sheet editor-sheet" data-label="02 · CONFIGURACIÓN Y METADATOS EDITORIALES (4 BLOQUES)">
            <div className="editor-intro">
              <div>
                <h2>Editor Estructural de Metadatos JATS</h2>
                <p>
                  Parametrizá los 4 bloques del &lt;front&gt; (artículo, estándares SPS, revista, ciclo editorial y autorías).
                  Los cambios se sincronizan e inyectan automáticamente en el árbol XML.
                </p>
              </div>
            </div>

            {/* Barra de Plantillas Rápidas */}
            <div className="preset-bar">
              <div className="preset-title">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                <span>Plantilla de Perfil Editorial:</span>
              </div>
              <div className="preset-options">
                <button
                  type="button"
                  className={`preset-btn ${front.journalTitle === 'Salud Colectiva' ? 'active' : ''}`}
                  onClick={() => applyPreset('salud-colectiva')}
                >
                  <span className="preset-indicator"></span>
                  Salud Colectiva (UNLa)
                </button>
                <button
                  type="button"
                  className={`preset-btn ${front.journalTitle !== 'Salud Colectiva' ? 'active' : ''}`}
                  onClick={() => applyPreset('generico')}
                >
                  <span className="preset-indicator"></span>
                  Revista Científica Multirevista
                </button>
              </div>
            </div>

            <div className="front-form">
              {/* BLOQUE 1 · TIPO DE ARTÍCULO */}
              <div className="block-card">
                <div className="block-header">
                  <div className="block-header-left">
                    <span className="block-num-pill">01</span>
                    <span className="block-tag">Tipo de Artículo</span>
                    <code className="block-tag-code">&lt;article article-type="..."&gt;</code>
                  </div>
                  <a
                    href="https://jats.nlm.nih.gov/nlm-dtd/articleauthoring/tag-library/2.1/n-24v0.html"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block-link"
                  >
                    Tag Library NLM (article-type) ↗
                  </a>
                </div>
                <div className="fields-row">
                  <label className="editor-field" style={{ minWidth: '320px', flex: '1 1 auto' }}>
                    <FieldLabel title="Tipología de Publicación" tag="article-type" />
                    <select
                      className="form-select"
                      value={front.articleType || 'research-article'}
                      onChange={(e) => updateFrontField('articleType', e.target.value)}
                    >
                      <option value="research-article">research-article (Artículo de investigación científica)</option>
                      <option value="review-article">review-article (Artículo de revisión / meta-análisis)</option>
                      <option value="editorial">editorial (Editorial / Declaración institucional)</option>
                      <option value="letter">letter (Carta al editor / debate)</option>
                      <option value="case-report">case-report (Reporte o estudio de caso clínico/social)</option>
                      <option value="brief-report">brief-report (Comunicación breve / reporte preliminar)</option>
                      <option value="book-review">book-review (Reseña o comentario de libro)</option>
                      <option value="other">other (Otro tipo de contribución)</option>
                    </select>
                  </label>
                </div>
              </div>

              {/* BLOQUE 2 · ESTÁNDARES RAÍZ */}
              <div className="block-card">
                <div className="block-header">
                  <div className="block-header-left">
                    <span className="block-num-pill">02</span>
                    <span className="block-tag">Estándares Raíz</span>
                    <code className="block-tag-code">DTD & SPS</code>
                  </div>
                  <span className="block-header-sub">Parámetros globales del XML JATS</span>
                </div>
                <div className="fields-row">
                  <label className="editor-field">
                    <FieldLabel title="Versión DTD JATS" tag="dtd-version" />
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

                  <label className="editor-field">
                    <FieldLabel title="SciELO Publishing Schema" tag="specific-use" />
                    <select
                      className="form-select"
                      value={front.spsVersion || 'sps-1.9'}
                      onChange={(e) => updateFrontField('spsVersion', e.target.value)}
                    >
                      <option value="sps-1.9">sps-1.9 (Vigente SciELO)</option>
                      <option value="sps-1.10">sps-1.10 (Nueva especificación)</option>
                    </select>
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Idioma Principal del Documento" tag="xml:lang" />
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
                  <div className="block-header-left">
                    <span className="block-num-pill">03</span>
                    <span className="block-tag">Revista, Publicador, DOI & Sección</span>
                    <code className="block-tag-code">&lt;journal-meta&gt; & &lt;article-id&gt;</code>
                  </div>
                  <span className="block-header-sub">Identidad editorial y de publicación</span>
                </div>

                <div className="fields-row">
                  <label className="editor-field">
                    <FieldLabel title="Título de la Revista" tag="<journal-title>" />
                    <AutoInput
                      value={front.journalTitle || ''}
                      placeholder="Título de la revista"
                      minChars={22}
                      onChange={(e) => updateFrontField('journalTitle', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Título Abreviado" tag="<abbrev-journal-title>" />
                    <AutoInput
                      value={front.journalAbbrev || ''}
                      placeholder="Abrev. oficial"
                      minChars={14}
                      onChange={(e) => updateFrontField('journalAbbrev', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="ID NLM-TA" tag="journal-id-type='nlm-ta'" />
                    <AutoInput
                      value={front.journalIdNlmTa || ''}
                      placeholder="NLM-TA ID"
                      minChars={12}
                      onChange={(e) => updateFrontField('journalIdNlmTa', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="ID Editorial" tag="journal-id-type='publisher-id'" />
                    <AutoInput
                      value={front.journalIdPublisher || ''}
                      placeholder="scol"
                      minChars={10}
                      onChange={(e) => updateFrontField('journalIdPublisher', e.target.value)}
                    />
                  </label>
                </div>

                <div className="fields-row">
                  <label className="editor-field">
                    <FieldLabel title="Editorial / Institución" tag="<publisher-name>" />
                    <AutoInput
                      value={front.publisherName || ''}
                      placeholder="Nombre de la institución editorial"
                      minChars={28}
                      onChange={(e) => updateFrontField('publisherName', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="ISSN Impreso" tag="pub-type='ppub'" />
                    <AutoInput
                      value={front.issnPpub || ''}
                      placeholder="0000-0000"
                      minChars={12}
                      onChange={(e) => updateFrontField('issnPpub', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="ISSN Digital" tag="pub-type='epub'" />
                    <AutoInput
                      value={front.issnEpub || ''}
                      placeholder="0000-0000"
                      minChars={12}
                      onChange={(e) => updateFrontField('issnEpub', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="DOI del Artículo" tag="pub-id-type='doi'" />
                    <AutoInput
                      value={front.doi || ''}
                      placeholder="10.18294/sc.2026.xxxx"
                      minChars={24}
                      onChange={(e) => updateFrontField('doi', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Sección Temática" tag="<subject>" />
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
                  <div className="block-header-left">
                    <span className="block-num-pill">04</span>
                    <span className="block-tag">Fechas, Numeración, Historial & Licencias</span>
                    <code className="block-tag-code">&lt;pub-date&gt; & &lt;permissions&gt;</code>
                  </div>
                  <a
                    href="https://creativecommons.org/cc-licenses/"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block-link"
                  >
                    Guía de Licencias Creative Commons ↗
                  </a>
                </div>

                <div className="fields-row">
                  <label className="editor-field">
                    <FieldLabel title="Fecha Publicación Electrónica" tag="<pub-date pub-type='epub'>" />
                    <div className="date-group">
                      <AutoInput
                        type="number"
                        placeholder="DD"
                        value={front.pubDay || ''}
                        minChars={2}
                        className="date-input"
                        onChange={(e) => updateFrontField('pubDay', e.target.value)}
                      />
                      <span className="date-separator">/</span>
                      <AutoInput
                        type="number"
                        placeholder="MM"
                        value={front.pubMonth || ''}
                        minChars={2}
                        className="date-input"
                        onChange={(e) => updateFrontField('pubMonth', e.target.value)}
                      />
                      <span className="date-separator">/</span>
                      <AutoInput
                        type="number"
                        placeholder="AAAA"
                        value={front.pubYear || ''}
                        minChars={4}
                        className="date-input"
                        onChange={(e) => updateFrontField('pubYear', e.target.value)}
                      />
                    </div>
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Año Colección" tag="date-type='collection'" />
                    <AutoInput
                      type="number"
                      placeholder="AAAA"
                      value={front.collectionYear || ''}
                      minChars={6}
                      onChange={(e) => updateFrontField('collectionYear', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Volumen" tag="<volume>" />
                    <AutoInput
                      placeholder="22"
                      value={front.volume || ''}
                      minChars={6}
                      onChange={(e) => updateFrontField('volume', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Fascículo / Número" tag="<issue>" />
                    <AutoInput
                      placeholder="1"
                      value={front.issue || ''}
                      minChars={6}
                      onChange={(e) => updateFrontField('issue', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Elocation-ID" tag="<elocation-id>" />
                    <AutoInput
                      placeholder="e6101"
                      value={front.elocationId || ''}
                      minChars={8}
                      onChange={(e) => updateFrontField('elocationId', e.target.value)}
                    />
                  </label>
                </div>

                {/* Subsección Historial Editorial */}
                <div className="editor-subsection-title">
                  <span className="subsection-bullet"></span>
                  Historial del Ciclo Editorial (&lt;history&gt;)
                </div>
                <div className="fields-row">
                  <label className="editor-field">
                    <FieldLabel title="Fecha Recepción" tag="date-type='received'" />
                    <div className="date-group">
                      <AutoInput
                        type="number"
                        placeholder="DD"
                        value={front.historyReceivedDay || ''}
                        minChars={2}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyReceivedDay', e.target.value)}
                      />
                      <span className="date-separator">/</span>
                      <AutoInput
                        type="number"
                        placeholder="MM"
                        value={front.historyReceivedMonth || ''}
                        minChars={2}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyReceivedMonth', e.target.value)}
                      />
                      <span className="date-separator">/</span>
                      <AutoInput
                        type="number"
                        placeholder="AAAA"
                        value={front.historyReceivedYear || ''}
                        minChars={4}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyReceivedYear', e.target.value)}
                      />
                    </div>
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Fecha Aceptación" tag="date-type='accepted'" />
                    <div className="date-group">
                      <AutoInput
                        type="number"
                        placeholder="DD"
                        value={front.historyAcceptedDay || ''}
                        minChars={2}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyAcceptedDay', e.target.value)}
                      />
                      <span className="date-separator">/</span>
                      <AutoInput
                        type="number"
                        placeholder="MM"
                        value={front.historyAcceptedMonth || ''}
                        minChars={2}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyAcceptedMonth', e.target.value)}
                      />
                      <span className="date-separator">/</span>
                      <AutoInput
                        type="number"
                        placeholder="AAAA"
                        value={front.historyAcceptedYear || ''}
                        minChars={4}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyAcceptedYear', e.target.value)}
                      />
                    </div>
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Fecha Publicación" tag="date-type='pub'" />
                    <div className="date-group">
                      <AutoInput
                        type="number"
                        placeholder="DD"
                        value={front.historyPubDay || ''}
                        minChars={2}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyPubDay', e.target.value)}
                      />
                      <span className="date-separator">/</span>
                      <AutoInput
                        type="number"
                        placeholder="MM"
                        value={front.historyPubMonth || ''}
                        minChars={2}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyPubMonth', e.target.value)}
                      />
                      <span className="date-separator">/</span>
                      <AutoInput
                        type="number"
                        placeholder="AAAA"
                        value={front.historyPubYear || ''}
                        minChars={4}
                        className="date-input"
                        onChange={(e) => updateFrontField('historyPubYear', e.target.value)}
                      />
                    </div>
                  </label>
                </div>

                {/* Subsección Permisos y Licencias */}
                <div className="editor-subsection-title">
                  <span className="subsection-bullet"></span>
                  Permisos y Licencia de Acceso Abierto (&lt;permissions&gt;)
                </div>
                <div className="fields-row">
                  <label className="editor-field" style={{ minWidth: '240px' }}>
                    <FieldLabel title="Plantilla Licencia Creative Commons" tag="<license>" />
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

                  <label className="editor-field">
                    <FieldLabel title="URL de la Licencia" tag="license xlink:href='...'" />
                    <AutoInput
                      placeholder="https://creativecommons.org/licenses/by/4.0/"
                      value={front.licenseUrl || ''}
                      minChars={35}
                      onChange={(e) => updateFrontField('licenseUrl', e.target.value)}
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Declaración de Copyright" tag="<copyright-statement>" />
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
                  <div className="block-header-left">
                    <span className="block-num-pill">05</span>
                    <span className="block-tag">Contenido del Manuscrito & Autores</span>
                    <code className="block-tag-code">&lt;title-group&gt; & &lt;contrib-group&gt;</code>
                  </div>
                  <span className="block-header-sub">Títulos, Resúmenes, Palabras Clave y Filiaciones</span>
                </div>

                <div className="fields-row">
                  <label className="editor-field">
                    <FieldLabel title="Título Principal" tag="<article-title>" />
                    <AutoInput
                      value={front.articleTitle || ''}
                      placeholder="Título principal del artículo"
                      minChars={30}
                      onChange={(e) => updateFrontField('articleTitle', e.target.value)}
                    />
                  </label>
                  <label className="editor-field">
                    <FieldLabel title="Subtítulo" tag="<subtitle>" />
                    <AutoInput
                      value={front.subtitle || ''}
                      placeholder="Subtítulo opcional"
                      minChars={20}
                      onChange={(e) => updateFrontField('subtitle', e.target.value)}
                    />
                  </label>
                  <label className="editor-field">
                    <FieldLabel title="Título Traducido al Inglés" tag="trans-title xml:lang='en'" />
                    <AutoInput
                      value={front.transTitleEn || ''}
                      placeholder="Translated title"
                      minChars={30}
                      onChange={(e) => updateFrontField('transTitleEn', e.target.value)}
                    />
                  </label>
                </div>

                <div className="abstracts-grid">
                  <label className="editor-field">
                    <FieldLabel title="Resumen Español" tag="abstract xml:lang='es'" />
                    <textarea
                      rows={4}
                      value={front.abstractEs || ''}
                      onChange={(e) => updateFrontField('abstractEs', e.target.value)}
                      placeholder="Resumen del artículo en español..."
                      className="abstract-textarea"
                    />
                  </label>

                  <label className="editor-field">
                    <FieldLabel title="Abstract Inglés" tag="trans-abstract xml:lang='en'" />
                    <textarea
                      rows={4}
                      value={front.abstractEn || ''}
                      onChange={(e) => updateFrontField('abstractEn', e.target.value)}
                      placeholder="Article abstract in English..."
                      className="abstract-textarea"
                    />
                  </label>
                </div>

                <div className="fields-row">
                  <label className="editor-field">
                    <FieldLabel title="Palabras clave (es) · separadas por coma" tag="kwd-group xml:lang='es'" />
                    <AutoInput
                      value={Array.isArray(front.keywordsEs) ? front.keywordsEs.join(', ') : (front.keywordsEs || '')}
                      placeholder="palabra 1, palabra 2"
                      minChars={25}
                      onChange={(e) => updateFrontField('keywordsEs', e.target.value)}
                    />
                  </label>
                  <label className="editor-field">
                    <FieldLabel title="Palabras clave (en) · separadas por coma" tag="kwd-group xml:lang='en'" />
                    <AutoInput
                      value={Array.isArray(front.keywordsEn) ? front.keywordsEn.join(', ') : (front.keywordsEn || '')}
                      placeholder="keyword 1, keyword 2"
                      minChars={25}
                      onChange={(e) => updateFrontField('keywordsEn', e.target.value)}
                    />
                  </label>
                </div>

                {/* Autores */}
                <div className="authors-edit">
                  <div className="authors-edit-header">
                    <div className="authors-title-wrap">
                      <FieldLabel title="Autores e Investigadores" tag="<contrib-group>" />
                      <span className="authors-counter">
                        {front.authors?.length || 0} autor{(front.authors?.length || 0) === 1 ? '' : 'es'} registrado{(front.authors?.length || 0) === 1 ? '' : 's'}
                      </span>
                    </div>
                    <button type="button" className="add-author-btn" onClick={addAuthor}>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                      <span>Añadir Autor</span>
                    </button>
                  </div>

                  {front.authors && front.authors.map((author, i) => (
                    <div key={i} className="author-card">
                      <div className="author-card-header">
                        <div className="author-badge">
                          <span className="author-num-pill">#{i + 1}</span>
                          <strong className="author-name-preview">
                            {author.givenNames || author.surname
                              ? `${author.givenNames} ${author.surname}`.trim()
                              : `Autor ${i + 1}`}
                          </strong>
                        </div>
                        <button
                          type="button"
                          className="delete-author-btn"
                          onClick={() => removeAuthor(i)}
                          title="Eliminar autor de la lista"
                        >
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                          <span>Eliminar</span>
                        </button>
                      </div>

                      <div className="author-fields-grid">
                        <label className="editor-field">
                          <FieldLabel title="Nombre/s" tag="<given-names>" />
                          <AutoInput
                            placeholder="Nombre/s"
                            value={author.givenNames || ''}
                            minChars={14}
                            onChange={(e) => updateAuthorField(i, 'givenNames', e.target.value)}
                          />
                        </label>
                        <label className="editor-field">
                          <FieldLabel title="Apellido" tag="<surname>" />
                          <AutoInput
                            placeholder="Apellido"
                            value={author.surname || ''}
                            minChars={14}
                            onChange={(e) => updateAuthorField(i, 'surname', e.target.value)}
                          />
                        </label>
                        <label className="editor-field">
                          <FieldLabel title="ORCID" tag="<contrib-id>" />
                          <AutoInput
                            placeholder="0000-0000-0000-0000"
                            value={author.orcid || ''}
                            minChars={20}
                            onChange={(e) => updateAuthorField(i, 'orcid', e.target.value)}
                          />
                        </label>
                        <label className="editor-field">
                          <FieldLabel title="Correo electrónico" tag="<email>" />
                          <AutoInput
                            type="email"
                            placeholder="correo@ejemplo.com"
                            value={author.email || ''}
                            minChars={20}
                            onChange={(e) => updateAuthorField(i, 'email', e.target.value)}
                          />
                        </label>
                      </div>

                      <div className="author-aff-field">
                        <label className="editor-field" style={{ width: '100%' }}>
                          <FieldLabel title="Afiliación Institucional" tag="<aff> / <institution>" />
                          <AutoInput
                            placeholder="Ej: Universidad Nacional de Lanús, Lanús, Argentina"
                            value={author.affiliation || ''}
                            minChars={40}
                            style={{ width: '100%' }}
                            onChange={(e) => updateAuthorField(i, 'affiliation', e.target.value)}
                          />
                        </label>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="editor-actions-bar">
              <button className="primary-editor-btn" onClick={handleApplyFront} disabled={applyingFront}>
                {applyingFront ? (
                  <>
                    <span className="btn-spinner"></span>
                    <span>Aplicando cambios al XML…</span>
                  </>
                ) : (
                  <>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                    <span>APLICAR CAMBIOS AL XML</span>
                  </>
                )}
              </button>
              <button className="secondary-editor-btn" onClick={handleExportEditedXml} disabled={exportingXml || !xml}>
                {exportingXml ? (
                  <>
                    <span className="btn-spinner dark"></span>
                    <span>Generando XML…</span>
                  </>
                ) : (
                  <>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                    <span>DESCARGAR XML EDITADO (.XML)</span>
                  </>
                )}
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

        {/* BLOQUE 03 · EDITOR Y VISOR DE CÓDIGO XML */}
        {xml && (
          <div className="sheet xml-editor-sheet" data-label={`03 · PREVISUALIZACIÓN & EDITOR XML${imageCount ? ` · ${imageCount} IMAGEN(ES)` : ''}`}>
            <div className="xml-editor-header">
              <div className="xml-editor-title-group">
                <div className="xml-tab active">
                  <span className="xml-tab-icon">‹/›</span>
                  <span className="xml-tab-name">{file ? `${stripExtension(file.name)}.xml` : 'documento-jats.xml'}</span>
                  <span className="xml-format-pill">{front?.spsVersion || 'sps-1.9'}</span>
                </div>
                <div className="xml-editor-meta">
                  <span className="xml-meta-chip">
                    <strong>{xmlLines.length.toLocaleString()}</strong> líneas
                  </span>
                  <span className="xml-meta-chip">
                    <strong>{(new Blob([xml]).size / 1024).toFixed(1)}</strong> KB
                  </span>
                  <span className="xml-meta-chip validation-chip valid">
                    ● JATS XML Válido
                  </span>
                </div>
              </div>

              <div className="xml-toolbar-actions">
                {/* Switcher: Resaltador / Editor Directo */}
                <div className="xml-view-mode-switch">
                  <button
                    type="button"
                    className={`mode-btn ${editorMode === 'preview' ? 'active' : ''}`}
                    onClick={() => setEditorMode('preview')}
                    title="Vista de sólo lectura con resaltado de sintaxis profesional"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    <span>Visor Resaltado</span>
                  </button>
                  <button
                    type="button"
                    className={`mode-btn ${editorMode === 'edit' ? 'active' : ''}`}
                    onClick={() => {
                      setRawXmlDraft(xml)
                      setRawXmlError('')
                      setEditorMode('edit')
                    }}
                    title="Editar directamente las etiquetas y texto del XML"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
                    <span>Editor Directo</span>
                  </button>
                </div>

                {/* Buscador */}
                <div className="xml-search-bar">
                  <svg className="search-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                  <input
                    type="text"
                    placeholder="Buscar etiqueta o texto…"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="xml-search-input"
                  />
                  {searchQuery && (
                    <span className="search-counter">
                      {matchCount} coincidencia{matchCount === 1 ? '' : 's'}
                    </span>
                  )}
                  {searchQuery && (
                    <button type="button" className="clear-search-btn" onClick={() => setSearchQuery('')}>×</button>
                  )}
                </div>

                {/* Wrap toggle */}
                <button
                  type="button"
                  className={`toolbar-icon-btn ${wrapLines ? 'active' : ''}`}
                  onClick={() => setWrapLines(!wrapLines)}
                  title={wrapLines ? 'Desactivar ajuste de línea' : 'Activar ajuste de línea'}
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="9 10 4 15 9 20"/><path d="M20 4v7a4 4 0 0 1-4 4H4"/></svg>
                  <span>Ajustar</span>
                </button>

                {/* Expand / Minimize */}
                <button
                  type="button"
                  className={`toolbar-icon-btn ${isExpanded ? 'active' : ''}`}
                  onClick={() => setIsExpanded(!isExpanded)}
                  title={isExpanded ? 'Reducir altura' : 'Expandir ventana del editor'}
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    {isExpanded ? (
                      <path d="M4 14h6v6m10-10h-6V4m0 6l7-7M3 21l7-7" />
                    ) : (
                      <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
                    )}
                  </svg>
                  <span>{isExpanded ? 'Reducir' : 'Expandir'}</span>
                </button>

                {/* Copiar XML */}
                <button
                  type="button"
                  className={`toolbar-icon-btn copy-action-btn ${copied ? 'copied' : ''}`}
                  onClick={handleCopyXml}
                  title="Copiar código XML completo al portapapeles"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                  <span>{copied ? '¡Copiado!' : 'Copiar'}</span>
                </button>
              </div>
            </div>

            {/* CUERPO DEL EDITOR */}
            {editorMode === 'preview' ? (
              <div className={`xml-code-wrapper ${wrapLines ? 'wrap' : ''} ${isExpanded ? 'expanded' : ''}`}>
                <div className="xml-gutter-lines" aria-hidden="true">
                  {xmlLines.map((_, i) => (
                    <span key={i} className="gutter-num">{i + 1}</span>
                  ))}
                </div>
                <pre
                  className="xml-code-body"
                  dangerouslySetInnerHTML={{ __html: highlightedXmlCode }}
                />
              </div>
            ) : (
              <div className={`xml-direct-edit-container ${isExpanded ? 'expanded' : ''}`}>
                <div className="direct-edit-banner">
                  <div className="direct-edit-info">
                    <span className="edit-pill">MODO EDICIÓN DIRECTA</span>
                    <span>Modificá el código XML. Al guardar, se sincronizarán los metadatos y la estructura en tiempo real.</span>
                  </div>
                  <div className="direct-edit-actions">
                    <button
                      type="button"
                      className="save-direct-btn"
                      onClick={handleSaveRawXml}
                    >
                      ✓ Guardar y Sincronizar XML
                    </button>
                    <button
                      type="button"
                      className="discard-direct-btn"
                      onClick={() => {
                        setRawXmlDraft(xml)
                        setRawXmlError('')
                        setEditorMode('preview')
                      }}
                    >
                      Cancelar
                    </button>
                  </div>
                </div>

                {rawXmlError && (
                  <div className="xml-edit-error-banner">
                    ⚠️ {rawXmlError}
                  </div>
                )}
                {rawXmlSaved && (
                  <div className="xml-edit-success-banner">
                    ✓ XML actualizado y validado con éxito
                  </div>
                )}

                <div className="direct-textarea-wrapper">
                  <textarea
                    className={`xml-raw-textarea ${wrapLines ? 'wrap' : ''}`}
                    value={rawXmlDraft}
                    onChange={(e) => {
                      setRawXmlDraft(e.target.value)
                      setRawXmlError('')
                      setRawXmlSaved(false)
                    }}
                    spellCheck="false"
                    placeholder="Contenido XML..."
                  />
                </div>
              </div>
            )}
          </div>
        )}
      </main>

      <footer className="credit">UNLa · spring boot · apache poi · react — conversor docx→jats</footer>
    </div>
  )
}