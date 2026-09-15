import { useMemo, useRef, useState } from 'react'
import { XMLBuilder, XMLParser } from 'fast-xml-parser'

const REPEATABLE = new Set(['contrib', 'aff', 'kwd', 'sec', 'fig', 'table-wrap', 'ref'])
const parser = new XMLParser({
  preserveOrder: true,
  ignoreAttributes: false,
  attributeNamePrefix: '@_',
  textNodeName: '#text',
  parseTagValue: false,
  trimValues: false,
  processEntities: true,
  isArray: (tagName) => REPEATABLE.has(tagName),
})
const builder = new XMLBuilder({
  preserveOrder: true,
  ignoreAttributes: false,
  attributeNamePrefix: '@_',
  textNodeName: '#text',
  format: true,
  indentBy: '  ',
  suppressEmptyNode: false,
  processEntities: true,
})

function firstNode(nodes, tag) {
  if (!Array.isArray(nodes)) return null
  for (const node of nodes) {
    if (node[tag]) return node
    for (const value of Object.values(node)) {
      if (Array.isArray(value)) {
        const found = firstNode(value, tag)
        if (found) return found
      }
    }
  }
  return null
}

function directNode(nodes, tag) {
  return (nodes || []).find((node) => node[tag]) || null
}

function childrenOf(node, tag) {
  return node?.[tag] || []
}

function textOf(node) {
  if (Array.isArray(node)) return node.map(textOf).join('')
  if (!node) return ''
  return (node['#text'] || '') + Object.entries(node)
    .filter(([key]) => key !== '#text' && !key.startsWith('@_'))
    .map(([, value]) => Array.isArray(value) ? value.map(textOf).join('') : '')
    .join('')
}

function setText(node, value) {
  if (Array.isArray(node)) node = node[0]
  if (!node) return
  const attrs = Object.entries(node).filter(([key]) => key.startsWith('@_'))
  Object.keys(node).forEach((key) => {
    if (!key.startsWith('@_')) delete node[key]
  })
  Object.assign(node, Object.fromEntries(attrs))
  node['#text'] = value || ''
}

function attrsOf(node) {
  if (Array.isArray(node)) node = node[0]
  if (!node) return {}
  return Object.fromEntries(Object.entries(node).filter(([key]) => key.startsWith('@_')))
}

function makeElement(tag, attrs = {}, children = []) {
  return { [tag]: [{ ...attrs, ...Object.fromEntries(children.map((child) => Object.entries(child)[0])) }] }
}

function appendElement(parent, tag, attrs = {}) {
  if (!parent?.[tag]) parent[tag] = []
  const child = makeElement(tag, attrs)
  parent[tag].push(child[tag][0])
  return child[tag][0]
}

function directChildren(node, tag) {
  return node ? Object.values(node).flatMap((value) => Array.isArray(value) ? value.filter((child) => child[tag]) : []) : []
}

function downloadXml(xml, filename) {
  const blob = new Blob([xml], { type: 'application/xml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function Field({ label, value, onChange, multiline = false }) {
  const Component = multiline ? 'textarea' : 'input'
  return (
    <label className="jats-field">
      <span>{label}</span>
      <Component value={value} onChange={(event) => onChange(event.target.value)} rows={multiline ? 4 : undefined} />
    </label>
  )
}

function SectionTree({ node, onChange }) {
  const section = node.sec
  const title = directNode(section, 'title')
  const paragraphs = directChildren(section, 'p')
  const nested = directChildren(section, 'sec')

  return (
    <div className="jats-section-node">
      <Field label="Título de sección" value={textOf(title)} onChange={(value) => { setText(title, value); onChange() }} />
      {paragraphs.map((paragraph, index) => (
        <Field key={index} label={`Párrafo ${index + 1}`} value={textOf(paragraph)} multiline onChange={(value) => { setText(paragraph, value); onChange() }} />
      ))}
      {nested.map((child, index) => <SectionTree key={index} node={child} onChange={onChange} />)}
    </div>
  )
}

export default function JatsXmlEditor() {
  const inputRef = useRef(null)
  const [documentTree, setDocumentTree] = useState(null)
  const [fileName, setFileName] = useState('documento-jats.xml')
  const [error, setError] = useState('')
  const [, refresh] = useState(0)

  const article = useMemo(() => documentTree && firstNode(documentTree, 'article'), [documentTree])
  const front = article && directNode(article.article, 'front')
  const body = article && directNode(article.article, 'body')
  const back = article && directNode(article.article, 'back')
  const articleMeta = front && directNode(front.front, 'article-meta')
  const journalMeta = front && directNode(front.front, 'journal-meta')
  const title = articleMeta && directNode(articleMeta['article-meta'], 'title-group')
  const articleTitle = title && directNode(title['title-group'], 'article-title')
  const abstract = articleMeta && directNode(articleMeta['article-meta'], 'abstract')
  const contribGroup = articleMeta && directNode(articleMeta['article-meta'], 'contrib-group')
  const refList = back && directNode(back.back, 'ref-list')

  const changed = () => refresh((value) => value + 1)

  const loadFile = (file) => {
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      try {
        const parsed = parser.parse(String(reader.result))
        if (!firstNode(parsed, 'article')) throw new Error('El documento no contiene un elemento <article>.')
        setDocumentTree(parsed)
        setFileName(file.name.replace(/\.xml$/i, '') + '-editado.xml')
        setError('')
      } catch (parseError) {
        setError(`XML inválido: ${parseError.message}`)
      }
    }
    reader.onerror = () => setError('No se pudo leer el archivo XML.')
    reader.readAsText(file)
  }

  const serialize = () => {
    if (!documentTree) return ''
    return `<?xml version="1.0" encoding="UTF-8"?>\n${builder.build(documentTree)}`
  }

  const updateTagText = (parent, tag, value) => {
    const node = directNode(parent, tag)
    if (node) setText(node[tag], value)
    changed()
  }

  const addAuthor = () => {
    if (!contribGroup) return
    const contrib = appendElement(contribGroup['contrib-group'], 'contrib', { '@_contrib-type': 'author' })
    const name = appendElement(contrib, 'name')
    appendElement(name, 'surname')['#text'] = 'Apellido'
    appendElement(name, 'given-names')['#text'] = 'Nombre'
    changed()
  }

  const removeAuthor = (contrib) => {
    contribGroup['contrib-group'] = contribGroup['contrib-group'].filter((item) => item !== contrib)
    changed()
  }

  const addAffiliation = () => {
    if (!articleMeta) return
    const aff = appendElement(articleMeta['article-meta'], 'aff', { '@_id': `aff${directChildren(articleMeta['article-meta'], 'aff').length + 1}` })
    appendElement(aff, 'institution')['#text'] = 'Nueva institución'
    changed()
  }

  const addReference = () => {
    if (!refList) return
    const ref = appendElement(refList['ref-list'], 'ref', { '@_id': `B${directChildren(refList['ref-list'], 'ref').length + 1}` })
    appendElement(ref, 'label')['#text'] = String(directChildren(refList['ref-list'], 'ref').length)
    appendElement(ref, 'mixed-citation')['#text'] = 'Autor. Título. Fuente; año.'
    changed()
  }

  const exportXml = () => {
    try {
      downloadXml(serialize(), fileName)
      setError('')
    } catch (serializationError) {
      setError(`No se pudo serializar el XML: ${serializationError.message}`)
    }
  }

  return (
    <section className="jats-editor" aria-label="Editor JATS XML">
      <div className="jats-editor-heading">
        <div>
          <span className="editor-kicker">EDITOR ESTRUCTURAL</span>
          <h2>JATS XML</h2>
          <p>Editá front, cuerpo y referencias conservando el orden y los atributos del documento.</p>
        </div>
        <div className="jats-file-actions">
          <input ref={inputRef} type="file" accept=".xml,text/xml" hidden onChange={(event) => loadFile(event.target.files?.[0])} />
          <button className="secondary" onClick={() => inputRef.current?.click()}>Abrir XML</button>
          <button className="primary" onClick={exportXml} disabled={!documentTree}>Descargar XML</button>
        </div>
      </div>

      {error && <div className="jats-error" role="alert">{error}</div>}
      {!documentTree && <div className="jats-empty" onClick={() => inputRef.current?.click()}><strong>Soltá un XML JATS acá</strong><span>o elegí un archivo para comenzar a editarlo</span></div>}

      {documentTree && <div className="jats-editor-grid">
        <div className="jats-main-column">
          <section className="jats-panel">
            <div className="jats-panel-header"><span>01 / FRONT-MATTER</span><strong>Identidad editorial</strong></div>
            <div className="jats-form-grid">
              <Field label="Título del artículo" value={textOf(articleTitle?.['article-title'])} onChange={(value) => updateTagText(title['title-group'], 'article-title', value)} />
              <Field label="ISSN electrónico" value={textOf(directNode(journalMeta?.['journal-meta'], 'issn')?.issn)} onChange={(value) => updateTagText(journalMeta['journal-meta'], 'issn', value)} />
              <Field label="Editorial" value={textOf(directNode(journalMeta?.['journal-meta'], 'publisher')?.publisher && directNode(directNode(journalMeta['journal-meta'], 'publisher').publisher, 'publisher-name')?.['publisher-name'])} onChange={(value) => updateTagText(journalMeta['journal-meta'], 'publisher-name', value)} />
              <Field label="Resumen" value={textOf(directNode(articleMeta?.['article-meta'], 'abstract')?.abstract)} multiline onChange={(value) => updateTagText(articleMeta['article-meta'], 'abstract', value)} />
            </div>
          </section>

          {contribGroup && <section className="jats-panel">
            <div className="jats-panel-header"><span>02 / CONTRIBUCIONES</span><strong>Autores y afiliaciones</strong><button className="tiny-button" onClick={addAuthor}>+ Autor</button></div>
            <div className="jats-list">
              {directChildren(contribGroup['contrib-group'], 'contrib').map((contrib, index) => {
                const name = directNode(contrib.contrib, 'name')
                return <div className="jats-list-row" key={index}>
                  <Field label="Nombre" value={textOf(directNode(name?.name, 'given-names')?.['given-names'])} onChange={(value) => { updateTagText(name.name, 'given-names', value) }} />
                  <Field label="Apellido" value={textOf(directNode(name?.name, 'surname')?.surname)} onChange={(value) => { updateTagText(name.name, 'surname', value) }} />
                  <button className="icon-button" title="Eliminar autor" onClick={() => removeAuthor(contrib)}>×</button>
                </div>
              })}
            </div>
            <button className="text-action" onClick={addAffiliation}>+ Agregar afiliación</button>
            {directChildren(articleMeta['article-meta'], 'aff').map((aff, index) => <div className="inline-record" key={index}><code>{attrsOf(aff.aff)['@_id'] || `aff${index + 1}`}</code><input value={textOf(directNode(aff.aff, 'institution')?.institution)} onChange={(event) => { updateTagText(aff.aff, 'institution', event.target.value) }} /></div>)}
          </section>}

          <section className="jats-panel">
            <div className="jats-panel-header"><span>03 / BODY</span><strong>Secciones y contenido</strong></div>
            {body ? directChildren(body.body, 'sec').map((section, index) => <SectionTree key={index} node={section} onChange={changed} />) : <p className="muted">Este documento no contiene &lt;body&gt;.</p>}
          </section>
        </div>

        <aside className="jats-side-column">
          <section className="jats-panel compact">
            <div className="jats-panel-header"><span>04 / BACK-MATTER</span><strong>Referencias</strong><button className="tiny-button" onClick={addReference}>+ Ref.</button></div>
            {refList ? directChildren(refList['ref-list'], 'ref').map((ref, index) => <div className="reference-row" key={index}><code>{attrsOf(ref.ref)['@_id'] || `B${index + 1}`}</code><textarea value={textOf(directNode(ref.ref, 'mixed-citation')?.['mixed-citation'])} onChange={(event) => { updateTagText(ref.ref, 'mixed-citation', event.target.value) }} rows={3} /></div>) : <p className="muted">No hay &lt;ref-list&gt;.</p>}
          </section>
          <section className="jats-panel compact jats-xml-status">
            <span className="editor-kicker">DOCUMENTO</span>
            <strong>{fileName.replace('-editado.xml', '.xml')}</strong>
            <span className="valid-mark">● XML cargado y editable</span>
          </section>
        </aside>
      </div>}
    </section>
  )
}
