package com.converter.docxjats.service.jats;

import com.converter.docxjats.service.DocxToJatsConverter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class RunRendererTest {

    @Test
    void rendersBibliographicCitationWithClosedRidAttribute() {
        XWPFDocument document = new XWPFDocument();
        var paragraph = document.createParagraph();
        var run = paragraph.createRun();
        run.setText("1");
        run.setVerticalAlignment("superscript");

        String rendered = new RunRenderer(new ImageRegistry()).render(paragraph);

        assertThat(rendered, is("<xref ref-type=\"bibr\" rid=\"B1\">1</xref>"));
    }

    @Test
    void testConvertSampleDocx() throws Exception {
        DocxToJatsConverter converter = new DocxToJatsConverter();
        Path p6032 = Path.of("../ID-6032_Perner+et+al_Word-XML.docx");
        if (Files.exists(p6032)) {
            var res6032 = converter.convert(new FileInputStream(p6032.toFile()), "ID-6032_Perner+et+al_Word-XML.docx");
            Files.writeString(Path.of("/tmp/converted-6032.xml"), res6032.getXml());
            System.out.println("Converted 6032: " + res6032.getXml().length() + " chars");
        }
        Path p5939 = Path.of("../ID-5939_Eich et al_XML-es (2).docx");
        if (Files.exists(p5939)) {
            var res5939 = converter.convert(new FileInputStream(p5939.toFile()), "ID-5939_Eich et al_XML-es (2).docx");
            Files.writeString(Path.of("/tmp/converted-5939.xml"), res5939.getXml());
            System.out.println("Converted 5939: " + res5939.getXml().length() + " chars");
        }
    }
}
