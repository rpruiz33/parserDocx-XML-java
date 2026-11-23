package com.converter.docxjats.service;

import com.converter.docxjats.dto.ComplianceReportDto;
import com.converter.docxjats.service.impl.ComplianceValidationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ComplianceValidationServiceTest {

    private ComplianceValidationServiceImpl service;

    @BeforeEach
    public void setUp() {
        service = new ComplianceValidationServiceImpl();
    }

    @Test
    public void testCompleteXmlIsAptoForBothPmcAndRedalyc() {
        String completeXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE article PUBLIC "-//NLM//DTD JATS (Z39.96) Journal Publishing DTD v1.1 20151215//EN" "https://jats.nlm.nih.gov/publishing/1.1/JATS-journalpublishing1.dtd">
            <article article-type="research-article" dtd-version="1.1" specific-use="sps-1.9" xml:lang="es" xmlns:xlink="http://www.w3.org/1999/xlink">
              <front>
                <journal-meta>
                  <journal-title>Salud Colectiva</journal-title>
                  <issn pub-type="epub">1851-8265</issn>
                  <publisher><publisher-name>Universidad Nacional de Lanús</publisher-name></publisher>
                </journal-meta>
                <article-meta>
                  <article-id pub-id-type="doi">10.18294/sc.2026.6032</article-id>
                  <title-group>
                    <article-title>Título en Español</article-title>
                    <trans-title xml:lang="en">Title in English</trans-title>
                  </title-group>
                  <contrib-group>
                    <contrib contrib-type="author" corresp="yes">
                      <name><surname>Pérez</surname><given-names>Juan</given-names></name>
                      <contrib-id contrib-id-type="orcid">https://orcid.org/0000-0002-1825-0097</contrib-id>
                      <email>autor@unla.edu.ar</email>
                      <xref ref-type="aff" rid="aff1">1</xref>
                    </contrib>
                  </contrib-group>
                  <aff id="aff1">
                    <institution content-type="orgname">Universidad Nacional de Lanús</institution>
                    <country country="AR">Argentina</country>
                  </aff>
                  <pub-date pub-type="epub">
                    <year>2026</year>
                  </pub-date>
                  <volume>23</volume>
                  <elocation-id>e6032</elocation-id>
                  <history>
                    <date date-type="received"><year>2025</year></date>
                    <date date-type="accepted"><year>2026</year></date>
                  </history>
                  <permissions>
                    <license license-type="open-access" xlink:href="https://creativecommons.org/licenses/by/4.0/">
                      <license-p>CC-BY 4.0</license-p>
                    </license>
                  </permissions>
                  <abstract><p>Resumen en español.</p></abstract>
                  <trans-abstract xml:lang="en"><p>Abstract in English.</p></trans-abstract>
                  <kwd-group xml:lang="es">
                    <kwd>salud</kwd>
                    <kwd>colectiva</kwd>
                  </kwd-group>
                  <kwd-group xml:lang="en">
                    <kwd>health</kwd>
                    <kwd>public</kwd>
                  </kwd-group>
                </article-meta>
              </front>
              <body>
                <sec><title>Introducción</title><p>Contenido del artículo.</p></sec>
                <sec sec-type="data-availability"><title>Disponibilidad de Datos</title><p>Datos abiertos.</p></sec>
              </body>
              <back>
                <fn-group>
                  <fn fn-type="conflict"><p>Sin conflicto de intereses.</p></fn>
                </fn-group>
                <ref-list>
                  <ref id="B1">
                    <element-citation publication-type="journal">
                      <person-group person-group-type="author"><name><surname>Gómez</surname></name></person-group>
                      <year>2024</year>
                    </element-citation>
                  </ref>
                </ref-list>
              </back>
            </article>
            """;

        ComplianceReportDto report = service.auditCompliance(completeXml);
        assertThat(report.checksPmc().size(), is(28));
        assertThat(report.checksRedalyc().size(), is(18));
        assertThat(report.pmcApto(), is(true));
        assertThat(report.redalycApto(), is(true));
    }

    @Test
    public void testMissingBilingualTitlesFailsRedalyc() {
        String monolingualXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <article article-type="research-article" dtd-version="1.1" specific-use="sps-1.9" xml:lang="es">
              <front>
                <journal-meta><journal-title>Salud Colectiva</journal-title></journal-meta>
                <article-meta>
                  <article-id pub-id-type="doi">10.18294/sc.2026.6032</article-id>
                  <title-group><article-title>Solo español</article-title></title-group>
                  <contrib-group><contrib contrib-type="author"><name><surname>Pérez</surname></name></contrib></contrib-group>
                  <abstract><p>Resumen</p></abstract>
                </article-meta>
              </front>
              <body><p>Texto</p></body>
              <back></back>
            </article>
            """;

        ComplianceReportDto report = service.auditCompliance(monolingualXml);
        assertThat(report.redalycApto(), is(false));
    }

    @Test
    public void testInvalidOrcidFailsPmc() {
        String invalidOrcidXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <article article-type="research-article" dtd-version="1.1" specific-use="sps-1.9" xml:lang="es">
              <front>
                <journal-meta><journal-title>Salud Colectiva</journal-title></journal-meta>
                <article-meta>
                  <article-id pub-id-type="doi">10.18294/sc.2026.6032</article-id>
                  <contrib-group>
                    <contrib contrib-type="author">
                      <name><surname>Pérez</surname></name>
                      <contrib-id contrib-id-type="orcid">0000-0002-1825-0097</contrib-id>
                    </contrib>
                  </contrib-group>
                </article-meta>
              </front>
              <body><p>Texto</p></body>
              <back></back>
            </article>
            """;

        ComplianceReportDto report = service.auditCompliance(invalidOrcidXml);
        assertThat(report.pmcApto(), is(false));
    }
}
