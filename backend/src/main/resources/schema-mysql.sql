-- =====================================================================
-- Esquema Minimalista de Tabla Única para Almacenar XML JATS
-- =====================================================================

CREATE DATABASE IF NOT EXISTS docx_jats_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE docx_jats_db;

CREATE TABLE IF NOT EXISTS documentos_xml (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_archivo VARCHAR(200) NOT NULL,
    codigo_articulo VARCHAR(50) NULL,
    contenido_xml LONGTEXT NOT NULL,
    es_valido BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_codigo (codigo_articulo),
    INDEX idx_nombre (nombre_archivo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
