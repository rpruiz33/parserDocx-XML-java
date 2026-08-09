import { useCallback, useRef, useState } from 'react'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8081'

function highlightXml(xml) {
  // Resaltado simple de tags para la vista previa (solo visual, no altera el XML real)
  const escaped = xml
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(/(&lt;\/?[a-zA-Z0-9\-:]+)/g, '<span class="tag">$1</span>')
}

export default function App() {
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [xml, setXml] = useState('')
  const [imageCount, setImageCount] = useState(0)
  const [status, setStatus] = useState(null) // {type: 'ok'|'error', message}
  const [loading, setLoading] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const inputRef = useRef(null)

  const pickFile = useCallback((f) => {
    if (!f) return
    if (!f.name.toLowerCase().endsWith('.docx')) {
      setStatus({ type: 'error', message: 'El archivo debe ser .docx' })
      return
    }
    setFile(f)
    setXml('')
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
    try {
      const form = new FormData()
      form.append('file', file)
      const res = await fetch(`${API_BASE}/api/convert/preview`, { method: 'POST', body: form })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || 'Error al convertir')
      setXml(data.xml)
      setImageCount(data.imageCount || 0)
      setStatus({ type: 'ok', message: `Conversión lista · ${data.imageCount} imagen(es) detectada(s)` })
    } catch (err) {
      setStatus({ type: 'error', message: err.message })
    } finally {
      setLoading(false)
    }
  }

  const handleDownload = async () => {
    if (!file) return
    setDownloading(true)
    setStatus(null)
    try {
      const form = new FormData()
      form.append('file', file)
      const res = await fetch(`${API_BASE}/api/convert/xml`, { method: 'POST', body: form })
      if (!res.ok) {
        const data = await res.json().catch(() => ({}))
        throw new Error(data.error || 'Error al generar el XML')
      }
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = file.name.replace(/\.docx$/i, '') + '-jats.xml'
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
      setStatus({ type: 'ok', message: 'XML JATS descargado' })
    } catch (err) {
      setStatus({ type: 'error', message: err.message })
    } finally {
      setDownloading(false)
    }
  }

  return (
    <>
      <div className="masthead">
        <div className="eyebrow">Herramienta de conversión editorial</div>
        <h1>DOCX <span className="tag">→</span> JATS <span className="tag">&lt;xml/&gt;</span></h1>
        <p>
          Subí un manuscrito en Word y obtené su equivalente en JATS (Journal Article Tag Suite):
          secciones anidadas, listas, tablas e imágenes convertidas a marcado XML listo para
          sistemas editoriales.
        </p>
      </div>

      <div className="workspace">
        <div className="sheet" data-label="01 · Manuscrito">
          <label
            className={`dropzone ${dragging ? 'dragging' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
          >
            <input
              ref={inputRef}
              type="file"
              accept=".docx"
              onChange={(e) => pickFile(e.target.files?.[0])}
            />
            <div className="icon" />
            {file ? (
              <>
                <div className="filename">{file.name}</div>
                <div className="hint">Click o arrastrá otro archivo para reemplazarlo</div>
              </>
            ) : (
              <>
                <div className="hint">Arrastrá tu archivo .docx acá, o hacé click para elegirlo</div>
              </>
            )}
          </label>

          <div className="actions">
            <button className="primary" onClick={handlePreview} disabled={!file || loading}>
              {loading ? 'Convirtiendo…' : 'Convertir y previsualizar'}
            </button>
            <button className="secondary" onClick={handleDownload} disabled={!file || downloading}>
              {downloading ? 'Generando .xml…' : 'Descargar XML JATS (.xml)'}
            </button>
          </div>

          {status && (
            <div className={`status ${status.type}`}>
              {status.type === 'error' ? '✕ ' : '✓ '}{status.message}
            </div>
          )}
        </div>

        {xml && (
          <div className="sheet" data-label={`02 · article.xml${imageCount ? ` · ${imageCount} imagen(es) en /images` : ''}`}>
            <pre className="xml-view" dangerouslySetInnerHTML={{ __html: highlightXml(xml) }} />
          </div>
        )}
      </div>

      <footer className="credit">spring boot · apache poi · react — conversor docx→jats</footer>
    </>
  )
}
