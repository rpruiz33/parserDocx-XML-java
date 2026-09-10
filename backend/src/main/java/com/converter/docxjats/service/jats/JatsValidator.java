package com.converter.docxjats.service.jats;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
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

            // Validar <front> y sus hijos
            NodeList frontList = doc.getElementsByTagName("front");
            if (frontList.getLength() == 0) {
                errors.add("Missing mandatory <front> element.");
            } else {
                Element front = (Element) frontList.item(0);
                if (front.getElementsByTagName("journal-meta").getLength() == 0) {
                    errors.add("Missing mandatory <journal-meta> in <front>.");
                }
                if (front.getElementsByTagName("article-meta").getLength() == 0) {
                    errors.add("Missing mandatory <article-meta> in <front>.");
                }
            }

            // Validar <body>
            if (doc.getElementsByTagName("body").getLength() == 0) {
                errors.add("Missing mandatory <body> element.");
            }

            // --- VALIDACIÓN DEL BACK ---
            if (doc.getElementsByTagName("back").getLength() == 0) {
                errors.add("Missing mandatory <back> element.");
            }

        } catch (Exception e) {
            errors.add("XML Parsing/Validation Error: " + e.getMessage());
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    public record ValidationResult(boolean isValid, List<String> errors) {}
}