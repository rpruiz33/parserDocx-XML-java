-- =====================================================================
-- Esquema Minimalista de Tabla Única para Almacenar XML JATS
-- =====================================================================

CREATE DATABASE IF NOT EXISTS docx_jats_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE docx_jats_db;

-- ---------------------------------------------------------------------
-- Tabla Única: documentos_xml
-- Almacena directamente el contenido completo del XML generado
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS documentos_xml (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_archivo VARCHAR(200) NOT NULL COMMENT 'Nombre del archivo .docx o .xml (ej: articulo-6032.docx)',
    codigo_articulo VARCHAR(50) NULL COMMENT 'Identificador del artículo si se conoce (ej: 6032, 5939)',
    contenido_xml LONGTEXT NOT NULL COMMENT 'Contenido XML JATS completo',
    es_valido BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Indica si pasó la validación de estructura JATS',
    fecha_creacion DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Fecha y hora de guardado',
    INDEX idx_codigo (codigo_articulo),
    INDEX idx_nombre (nombre_archivo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Ejemplo de Inserción
-- ---------------------------------------------------------------------
INSERT INTO documentos_xml (nombre_archivo, codigo_articulo, contenido_xml, es_valido)
VALUES (
    'articulo-6032.docx',
    '6032',
    '<?xml version="1.0" encoding="utf-8"?><!DOCTYPE article PUBLIC "-//NLM//DTD JATS (Z39.96) Journal Publishing DTD v1.1 20151215//EN" "https://jats.nlm.nih.gov/publishing/1.1/JATS-journalpublishing1.dtd"><article article-type="research-article" dtd-version="1.1" specific-use="sps-1.9" xml:lang="es"><front><journal-meta><journal-title>Salud Colectiva</journal-title></journal-meta><article-meta><article-id pub-id-type="publisher-id">6032</article-id></article-meta></front><body><p>Texto del artículo</p></body><back></back></article>',
    TRUE
);
