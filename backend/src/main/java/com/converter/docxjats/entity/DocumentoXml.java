package com.converter.docxjats.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad JPA correspondiente a la tabla `documentos_xml`.
 * Almacena el contenido íntegro del XML JATS y metadatos básicos de identificación.
 */
@Entity
@Table(name = "documentos_xml", indexes = {
        @Index(name = "idx_codigo", columnList = "codigo_articulo"),
        @Index(name = "idx_nombre", columnList = "nombre_archivo")
})
public class DocumentoXml {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_archivo", nullable = false, length = 200)
    private String nombreArchivo;

    @Column(name = "codigo_articulo", length = 50)
    private String codigoArticulo;

    @Lob
    @Basic(fetch = jakarta.persistence.FetchType.EAGER)
    @Column(name = "contenido_xml", nullable = false, columnDefinition = "LONGTEXT")
    private String contenidoXml;

    @Column(name = "es_valido", nullable = false)
    private boolean esValido = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public DocumentoXml() {
        this.fechaCreacion = LocalDateTime.now();
    }

    public DocumentoXml(String nombreArchivo, String codigoArticulo, String contenidoXml, boolean esValido) {
        this.nombreArchivo = nombreArchivo;
        this.codigoArticulo = codigoArticulo;
        this.contenidoXml = contenidoXml;
        this.esValido = esValido;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getCodigoArticulo() {
        return codigoArticulo;
    }

    public void setCodigoArticulo(String codigoArticulo) {
        this.codigoArticulo = codigoArticulo;
    }

    public String getContenidoXml() {
        return contenidoXml;
    }

    public void setContenidoXml(String contenidoXml) {
        this.contenidoXml = contenidoXml;
    }

    public boolean isEsValido() {
        return esValido;
    }

    public void setEsValido(boolean esValido) {
        this.esValido = esValido;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}

