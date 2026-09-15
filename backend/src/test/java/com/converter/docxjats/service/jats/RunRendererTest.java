package com.converter.docxjats.service.jats;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

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
}
