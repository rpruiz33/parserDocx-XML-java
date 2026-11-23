package com.converter.docxjats.service.interfaces;

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
     * Lista todos los documentos XML almacenados ordenados del más reciente al más antiguo.
     */
    List<DocumentoXml> listarTodos();

    /**
     * Elimina un documento XML por su ID.
     */
    boolean eliminarPorId(Long id);
}

