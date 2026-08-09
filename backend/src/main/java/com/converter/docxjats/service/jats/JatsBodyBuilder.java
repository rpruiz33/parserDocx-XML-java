package com.converter.docxjats.service.jats;

import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Construye el bloque {@code <body>} del artículo JATS: secciones anidadas
 * ({@code <sec>}), párrafos, listas, tablas e imágenes en figura.
 * El llamador (orquestador) decide, a partir de los estilos del .docx,
 * qué método invocar por cada elemento del documento.
 */
public class JatsBodyBuilder {

    private final StringBuilder body = new StringBuilder();
    private final Deque<Integer> openSectionLevels = new ArrayDeque<>();
    private final ListTracker listTracker = new ListTracker();
    private int tableCounter = 0;
    private int figCounter = 0;

    /** Abre un <sec> en el nivel indicado, cerrando los niveles iguales o más profundos que estén abiertos. */
    public void openSection(int level, String title) {
        listTracker.closeIfOpen(body);
        while (!openSectionLevels.isEmpty() && openSectionLevels.peek() >= level) {
            body.append("</sec>\n");
            openSectionLevels.pop();
        }
        body.append("<sec>\n<title>").append(XmlUtils.escape(title)).append("</title>\n");
        openSectionLevels.push(level);
    }

    public void appendParagraph(String runsXml) {
        if (runsXml == null || runsXml.isBlank()) return;
        listTracker.closeIfOpen(body);
        body.append("<p>").append(runsXml).append("</p>\n");
    }

    public void appendListItem(String listType, String numId, String runsXml) {
        listTracker.open(body, listType, numId);
        body.append("<list-item list-item-type=\"").append(listType).append("\"><p>").append(runsXml).append("</p></list-item>\n");
    }

    public void closeListIfOpen() {
        listTracker.closeIfOpen(body);
    }

    public void appendFigure(String filename) {
        listTracker.closeIfOpen(body);
        figCounter++;
        body.append("<fig type=\"figure\" id=\"fig").append(figCounter).append("\">\n")
            .append("<caption><p></p></caption>\n")
            .append("<graphic xlink:href=\"images/").append(filename).append("\"/>\n")
            .append("</fig>\n");
    }

    public void appendTable(XWPFTable table) {
        listTracker.closeIfOpen(body);
        tableCounter++;
        body.append("<table-wrap id=\"table").append(tableCounter).append("\">\n<table table-type=\"regular\">\n");
        List<XWPFTableRow> rows = table.getRows();
        boolean firstRow = true;
        for (XWPFTableRow row : rows) {
            if (firstRow) {
                body.append("<thead>\n<tr>\n");
                for (XWPFTableCell cell : row.getTableCells()) {
                    body.append("<th>").append(XmlUtils.escape(cell.getText())).append("</th>\n");
                }
                body.append("</tr>\n</thead>\n<tbody>\n");
                firstRow = false;
            } else {
                body.append("<tr>\n");
                for (XWPFTableCell cell : row.getTableCells()) {
                    body.append("<td>").append(XmlUtils.escape(cell.getText())).append("</td>\n");
                }
                body.append("</tr>\n");
            }
        }
        body.append("</tbody>\n</table>\n</table-wrap>\n");
    }

    /** Cierra listas y secciones pendientes y devuelve el XML final del <body>. */
    public String build() {
        listTracker.closeIfOpen(body);
        while (!openSectionLevels.isEmpty()) {
            body.append("</sec>\n");
            openSectionLevels.pop();
        }
        return "<body>\n" + body + "</body>\n";
    }

    /** Agrupa párrafos de lista consecutivos con el mismo numId dentro de <list>...</list>. */
    private static class ListTracker {
        private String openNumId = null;

        void open(StringBuilder target, String listType, String numId) {
            if (openNumId != null && !openNumId.equals(numId)) {
                target.append("</list>\n");
                openNumId = null;
            }
            if (openNumId == null) {
                target.append("<list list-type=\"").append(listType).append("\">\n");
                openNumId = numId;
            }
        }

        void closeIfOpen(StringBuilder target) {
            if (openNumId != null) {
                target.append("</list>\n");
                openNumId = null;
            }
        }
    }
}
