package com.converter.docxjats.service.jats;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class JatsValidator {

    public static ValidationResult validate(String xmlContent) {
        List<String> errors = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));

            String rootName = doc.getDocumentElement().getNodeName();
            if (!"article".equals(rootName)) {
                errors.add("Root element must be <article>, found: " + rootName);
            }

            if (doc.getElementsByTagName("front").getLength() == 0) {
                errors.add("Missing mandatory <front> element.");
            } else {
                if (doc.getElementsByTagName("journal-meta").getLength() == 0) {
                    errors.add("Missing mandatory <journal-meta> in <front>.");
                }
                if (doc.getElementsByTagName("article-meta").getLength() == 0) {
                    errors.add("Missing mandatory <article-meta> in <front>.");
                }
            }

            if (doc.getElementsByTagName("body").getLength() == 0) {
                errors.add("Missing mandatory <body> element.");
            }

        } catch (Exception e) {
            errors.add("XML Parsing/Validation Error: " + e.getMessage());
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public record ValidationResult(boolean isValid, List<String> errors) {}
}
