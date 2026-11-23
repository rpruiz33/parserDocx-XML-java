package com.converter.docxjats.repository;

import com.converter.docxjats.entity.DocumentoXml;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data JPA para la entidad {@link DocumentoXml}.
 */
@Repository
public interface DocumentoXmlRepository extends JpaRepository<DocumentoXml, Long> {

    List<DocumentoXml> findByCodigoArticulo(String codigoArticulo);

    Optional<DocumentoXml> findTopByCodigoArticuloOrderByIdDesc(String codigoArticulo);

    List<DocumentoXml> findAllByOrderByIdDesc();
}

