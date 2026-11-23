package com.converter.docxjats.service.impl;

import com.converter.docxjats.dto.DocumentoXmlResumenDto;
import com.converter.docxjats.entity.DocumentoXml;
import com.converter.docxjats.repository.DocumentoXmlRepository;
import com.converter.docxjats.service.interfaces.IDocumentoXmlService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementación del servicio de persistencia de documentos XML en MySQL / JPA.
 */
@Service
@Transactional
public class DocumentoXmlServiceImpl implements IDocumentoXmlService {

    private final DocumentoXmlRepository repository;

    public DocumentoXmlServiceImpl(DocumentoXmlRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentoXml guardar(String nombreArchivo, String codigoArticulo, String contenidoXml, boolean esValido) {
        if (contenidoXml == null || contenidoXml.isBlank()) {
            throw new IllegalArgumentException("El contenido XML no puede estar vacío.");
        }
        String archivo = (nombreArchivo != null && !nombreArchivo.isBlank()) ? nombreArchivo.trim() : "documento.xml";
        DocumentoXml entidad = new DocumentoXml(archivo, codigoArticulo, contenidoXml, esValido);
        return repository.save(entidad);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentoXml> obtenerPorId(Long id) {
        if (id == null) return Optional.empty();
        return repository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentoXml> obtenerUltimoPorCodigo(String codigoArticulo) {
        if (codigoArticulo == null || codigoArticulo.isBlank()) return Optional.empty();
        return repository.findTopByCodigoArticuloOrderByIdDesc(codigoArticulo.trim());
    }

    /**
     * Devuelve DTOs de resumen SIN el campo contenidoXml para evitar cargar
     * LOBs masivos al listar todos los documentos. Para obtener el XML completo,
     * usar {@link #obtenerPorId(Long)}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentoXmlResumenDto> listarResumenes() {
        return repository.findAllByOrderByIdDesc().stream()
                .map(doc -> new DocumentoXmlResumenDto(
                        doc.getId(),
                        doc.getNombreArchivo(),
                        doc.getCodigoArticulo(),
                        doc.isEsValido(),
                        doc.getFechaCreacion()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentoXml> listarTodos() {
        return repository.findAllByOrderByIdDesc();
    }

    @Override
    public boolean eliminarPorId(Long id) {
        if (id != null && repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
}

