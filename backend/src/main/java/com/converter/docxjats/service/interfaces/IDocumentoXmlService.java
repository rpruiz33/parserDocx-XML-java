package com.converter.docxjats.service.interfaces;

import com.converter.docxjats.dto.DocumentoXmlResumenDto;
import com.converter.docxjats.entity.DocumentoXml;
import java.util.List;
import java.util.Optional;

/**
 * Interfaz segregada para la persistencia y consulta de documentos XML en la base de datos.
 */
public interface IDocumentoXmlService {

    /**
     * Guarda un nuevo documento XML en la base de datos.
     */
    DocumentoXml guardar(String nombreArchivo, String codigoArticulo, String contenidoXml, boolean esValido);

    /**
     * Recupera un documento XML por su identificador primario.
     */
    Optional<DocumentoXml> obtenerPorId(Long id);

    /**
     * Recupera el XML más reciente para un código de artículo dado (ej. "6032").
     */
    Optional<DocumentoXml> obtenerUltimoPorCodigo(String codigoArticulo);

    /**
     * Lista todos los documentos como DTOs de resumen (sin contenidoXml)
     * para evitar cargar LOBs masivos en la respuesta de listado.
     */
    List<DocumentoXmlResumenDto> listarResumenes();

    /**
     * Lista todos los documentos XML almacenados ordenados del más reciente al más antiguo.
     * @deprecated Usar {@link #listarResumenes()} para listados, y {@link #obtenerPorId(Long)} para cargar el contenido completo.
     */
    @Deprecated
    List<DocumentoXml> listarTodos();

    /**
     * Elimina un documento XML por su ID.
     */
    boolean eliminarPorId(Long id);
}

