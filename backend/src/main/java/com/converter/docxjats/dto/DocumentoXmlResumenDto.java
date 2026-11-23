package com.converter.docxjats.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * DTO de resumen para la listado de documentos XML almacenados.
 * NO incluye el campo {@code contenidoXml} (que puede ser de 100 KB+),
 * evitando cargar todos los LOB en una sola respuesta JSON.
 * El contenido completo se obtiene via GET /api/documentos/{id}.
 */
public record DocumentoXmlResumenDto(
        Long id,
        String nombreArchivo,
        String codigoArticulo,
        boolean esValido,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime fechaCreacion
) {}

