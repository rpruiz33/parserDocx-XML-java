import { useCallback, useRef, useState } from 'react'
import axios from 'axios'
import './index.css'
import logoUnla from './assets/unla-logo.png'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8081'

function highlightXml(xml) {
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
  const [status, setStatus] = useState(null)
  const [loading, setLoading] = useState(false)
  const [downloading, setDownloading] = useState(false)
  
  // Estado y loader para la descarga por ID vía Axios (/jats)
  const [articleId, setArticleId] = useState('')
  const [downloadingByArticleId, setDownloadingByArticleId] = useState(false)

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
  if (!file) return;

  // Extrae los números del nombre del archivo (ej. de "ID-5939_..." extrae "5939")
  const match = file.name.match(/\d+/);
  const extractedId = match ? match[0] : null;

  if (!extractedId) {
    setStatus({ 
      type: 'error', 
      message: 'No se pudo detectar el ID del artículo en el nombre del archivo.' 
    });
    return;
  }

  // Llama a la función Axios para descargar desde /jats
  await getJatsXmlAxios(extractedId);
};

  // Función Axios para consumir el endpoint /jats pasándole articleId
 async function getJatsXmlAxios(idToFetch) {
  const id = idToFetch || articleId
  if (!id || !id.trim()) {
    setStatus({ type: 'error', message: 'Ingresá un ID de artículo válido (ej. 6032)' })
    return
  }

  setDownloadingByArticleId(true)
  setStatus(null)

  // 🔹 Actualiza la URL del navegador sin recargar la página
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
        <div className="sheet" data-label="01 · MANUSCRITO">
          <div
            className={`dropzone ${dragging ? 'dragging' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
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
            <button className="secondary" onClick={handleDownload} disabled={!file || downloading}>
              {downloading ? 'Generando .xml…' : 'DESCARGAR XML JATS (.XML)'}
            </button>
          </div>
        </div>

        <div className="sheet" data-label="02 · DESCARGA POR ID DE ARTÍCULO (/jats)">
          <div className="file-picker-row">
            <input
              type="text"
              placeholder="Ej: 6032 o 5939"
              value={articleId}
              onChange={(e) => setArticleId(e.target.value)}
              disabled={downloadingByArticleId}
              style={{
                padding: '0.6rem 0.8rem',
                borderRadius: '4px',
                border: '1px solid #ccc',
                flexGrow: 1
              }}
            />
            <button
              className="primary"
              onClick={() => getJatsXmlAxios()}
              disabled={downloadingByArticleId || !articleId.trim()}
            >
              {downloadingByArticleId ? 'DESCARGANDO...' : 'OBTENER JATS XML'}
            </button>
          </div>
        </div>

        {status && (
          <div className={`status ${status.type}`}>
            {status.type === 'error' ? '✕ ' : '✓ '}{status.message}
          </div>
        )}

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