package com.converter.docxjats.controller;

import com.converter.docxjats.service.jats.JatsFrontBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class JatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetJatsForArticle6032() throws Exception {
        mockMvc.perform(get("/jats/6032"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<article")))
                .andExpect(content().string(containsString("<journal-meta>")))
                .andExpect(content().string(containsString("<article-id pub-id-type=\"doi\">10.18294/sc.2026.6032</article-id>")))
                .andExpect(content().string(containsString("<article-title>Cesáreas como expresión biopolítica del nacimiento")))
                .andExpect(content().string(not(containsString("<funding-group>"))))
                .andExpect(content().string(containsString("fn-type=\"supported-by\"")))
                .andExpect(content().string(containsString("<body>")));
    }

    @Test
    public void testGetJatsForArticle5939() throws Exception {
        mockMvc.perform(get("/jats/5939"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<article")))
                .andExpect(content().string(containsString("<journal-meta>")))
                .andExpect(content().string(containsString("<article-id pub-id-type=\"doi\">10.18294/sc.2026.5939</article-id>")))
                .andExpect(content().string(containsString("<article-title>Prácticas de fin de vida y directivas anticipadas de voluntad")))
                .andExpect(content().string(containsString("<funding-group>")))
                .andExpect(content().string(containsString("<award-group")))
                .andExpect(content().string(containsString("fn-type=\"financial-disclosure\"")))
                .andExpect(content().string(containsString("<body>")));
    }

    @Test
    public void testPostJatsWithPayload() throws Exception {
        mockMvc.perform(post("/service/jats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleId\": \"6032\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("6032")));
    }

    @Test
    public void testFrontBuilderProducesStructuredJatsFront() {
        JatsFrontBuilder builder = new JatsFrontBuilder();
        builder.setSpsVersion("sps-1.9");
        builder.setLang("es");
        builder.setTitle("Artículo de ejemplo");
        builder.appendAuthor("Ana Pérez", List.of("aff1"), "0000-0001-2345-6789", false, null);
        builder.appendAffiliation("aff1", "1", "Universidad X, Ciudad, País.", "Universidad X", "Facultad de Salud",
                null, "Universidad X", "Ciudad", "Provincia", "País", "ana@example.com");
        builder.appendAbstractParagraph("<italic>Resumen</italic> de prueba.");
        builder.addKeywordEs("salud");
        builder.addKeywordEn("health");

        String xml = builder.build("Fallback title");

        assertThat(xml, containsString("<front"));
        assertThat(xml, containsString("<journal-meta>"));
        assertThat(xml, containsString("<article-meta"));
        assertThat(xml, containsString("specific-use=\"sps-1.9\""));
        assertThat(xml, containsString("xml:lang=\"es\""));
        assertThat(xml, containsString("<title-group>"));
        assertThat(xml, containsString("<contrib-group>"));
        assertThat(xml, containsString("<aff id=\"aff1\""));
        assertThat(xml, containsString("<abstract>"));
        assertThat(xml, containsString("<kwd-group"));
    }

    @Test
    public void testFrontBuilderUsesRequestedAuthorAndPublicationStructure() {
        JatsFrontBuilder builder = new JatsFrontBuilder();
        builder.setTitle("Artículo de ejemplo");
        builder.setVolume("22");
        builder.setElocationId("e5939");
        builder.setPubDate("11", "03", "2026");
        builder.appendAuthor("Ana Pérez", List.of("aff1"), "0000-0001-2345-6789", "Conceptualización", true, "ana@universidad.edu", false);
        builder.appendAuthor("Luis Gómez", List.of("aff1"), null, null, "Biógrafo del autor", false, null, false);
        builder.appendAffiliation("aff1", "1", "Universidad X", "Universidad X", "Facultad de Salud",
                null, "Universidad X", "Ciudad", "Provincia", "País", "contacto@universidad.edu");
        builder.addAuthorNoteFn("con", "fn-con", "Contribución", "Ana Pérez: conceptualización; Luis Gómez: revisión crítica.");

        String xml = builder.build("Fallback title");

        assertThat(xml, containsString("<pub-date date-type=\"pub\" publication-format=\"electronic\">"));
        assertThat(xml, containsString("<year>2026</year>"));
        assertThat(xml, containsString("<volume>22</volume>"));
        assertThat(xml, containsString("<elocation-id>e5939</elocation-id>"));
        assertThat(xml, containsString("<email>ana@universidad.edu</email>"));
        assertThat(xml, containsString("<fn fn-type=\"con\" id=\"fn-con\">"));
        assertThat(xml, containsString("<xref ref-type=\"aff\" rid=\"aff1\">"));
        assertThat(xml, not(containsString("<bio>")));
        assertThat(xml, not(containsString("<role vocab=\"CRediT\"")));
    }

    @Test
    public void testFrontEscapesProblematicXrefRidAttributes() throws Exception {
        JatsFrontBuilder builder = new JatsFrontBuilder();
        String rid = "aff“1<2";
        builder.setTitle("Artículo de ejemplo");
        builder.appendAffiliation(rid, "1", "Universidad X", "Universidad X", null,
            null, "Universidad X", "Ciudad", "Provincia", "País", null);
        builder.appendAuthor("Ana Pérez", List.of(rid), null, false, null);

        String xml = builder.build("Fallback title");
        Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(document.getElementsByTagName("xref").item(0).getAttributes()
                .getNamedItem("rid").getNodeValue(), containsString(rid));
    }

    @Test
    public void testFrontEditorModifiesFourBlocks() throws Exception {
        String baseXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article article-type="research-article" dtd-version="1.1" specific-use="sps-1.9" xml:lang="es" xmlns:xlink="http://www.w3.org/1999/xlink">
              <front>
                <journal-meta>
                  <journal-id journal-id-type="nlm-ta">Salud Colect</journal-id>
                  <journal-id journal-id-type="publisher-id">scol</journal-id>
                  <journal-title-group>
                    <journal-title>Salud Colectiva</journal-title>
                  </journal-title-group>
                </journal-meta>
                <article-meta>
                  <title-group>
                    <article-title>Título Inicial</article-title>
                  </title-group>
                </article-meta>
              </front>
              <body><p>Texto</p></body>
              <back></back>
            </article>
            """;

        com.converter.docxjats.dto.FrontFields fields = com.converter.docxjats.service.jats.JatsFrontEditor.extract(baseXml);
        assertThat(fields.getArticleType(), is("research-article"));
        assertThat(fields.getJournalTitle(), is("Salud Colectiva"));

        fields.setArticleType("review-article");
        fields.setDtdVersion("1.2");
        fields.setSpsVersion("sps-1.10");
        fields.setDoi("10.18294/sc.2026.9999");
        fields.setVolume("23");
        fields.setElocationId("e9999");
        fields.setSubject("Revisión Temática");
        fields.setLicenseUrl("https://creativecommons.org/licenses/by/4.0/");

        String updatedXml = com.converter.docxjats.service.jats.JatsFrontEditor.applyFront(baseXml, fields);
        assertThat(updatedXml, containsString("article-type=\"review-article\""));
        assertThat(updatedXml, containsString("dtd-version=\"1.2\""));
        assertThat(updatedXml, containsString("specific-use=\"sps-1.10\""));
        assertThat(updatedXml, containsString("<article-id pub-id-type=\"doi\">10.18294/sc.2026.9999</article-id>"));
        assertThat(updatedXml, containsString("<volume>23</volume>"));
        assertThat(updatedXml, containsString("<elocation-id>e9999</elocation-id>"));
        assertThat(updatedXml, containsString("<subject>Revisión Temática</subject>"));
        assertThat(updatedXml, containsString("xlink:href=\"https://creativecommons.org/licenses/by/4.0/\""));
    }

    @Test
    public void testInvalidArticleId() throws Exception {
        mockMvc.perform(get("/jats/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    public void testConvertControllerRejectsEmptyOrNonDocxFile() throws Exception {
        org.springframework.mock.web.MockMultipartFile textFile =
                new org.springframework.mock.web.MockMultipartFile("file", "documento.txt", "text/plain", "texto plano".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/convert/preview")
                        .file(textFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El archivo debe tener extensión .docx"));
    }

    @Test
    public void testFrontEditControllerEndpoints() throws Exception {
        String baseXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article article-type="research-article" dtd-version="1.1" specific-use="sps-1.9" xml:lang="es">
              <front>
                <journal-meta>
                  <journal-title-group><journal-title>Salud Colectiva</journal-title></journal-title-group>
                </journal-meta>
                <article-meta>
                  <title-group><article-title>Título de Prueba</article-title></title-group>
                </article-meta>
              </front>
              <body><p>Texto</p></body>
              <back></back>
            </article>
            """;

        // Test /api/convert/front-fields
        mockMvc.perform(post("/api/convert/front-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"xml\": " + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(baseXml) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleTitle").value("Título de Prueba"))
                .andExpect(jsonPath("$.journalTitle").value("Salud Colectiva"));

        // Test /api/convert/apply-front
        String payload = """
            {
              "xml": %s,
              "front": {
                "articleTitle": "Título Modificado",
                "doi": "10.18294/sc.2026.8888"
              }
            }
            """.formatted(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(baseXml));

        mockMvc.perform(post("/api/convert/apply-front")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xml").value(containsString("Título Modificado")))
                .andExpect(jsonPath("$.xml").value(containsString("10.18294/sc.2026.8888")))
                .andExpect(jsonPath("$.valid").value(true));
    }
}

