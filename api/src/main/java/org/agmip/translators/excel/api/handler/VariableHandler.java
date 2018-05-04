package org.agmip.translators.excel.api.handler;

import org.agmip.translators.excel.api.DataNode;
import org.agmip.translators.excel.api.Util;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class VariableHandler extends DefaultHandler {
    private static final Logger LOG = LoggerFactory.getLogger(VariableHandler.class);
    private final SharedStringsTable sst;
    private boolean foundRow = false;
    private boolean finished = false;
    private boolean nextIsString;
    private boolean inlineString;
    private String contents;
    private DataNode node;

    public VariableHandler(DataNode node, SharedStringsTable sst) {
        this.node = node;
        this.sst = sst;
    }

    @Override
    public void startDocument() throws SAXException {
        this.foundRow = false;
        this.finished = false;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (finished) return;
        switch (qName) {
            case "row":
                foundRow = true;
                break;
            case "c":
                if (foundRow) {
                    String cellType = attributes.getValue("t");
                    nextIsString = cellType != null && cellType.equals("s");
                    inlineString = cellType != null && cellType.equals("inlineStr");
                }
                break;
            default:
                break;
        }
        contents = "";
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (!finished) contents += new String(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (finished) return;
        if (nextIsString) {
            int idx = Integer.parseInt(contents);
            contents = new XSSFRichTextString(sst.getEntryAt(idx)).toString();
            nextIsString = false;
        }
        contents = Util.standardizeVariable(contents);
        switch(qName) {
            case "row":
                finished = true;
                break;
            case "v":
                if (contents.startsWith("!")) {
                    return;
                } else {
                    node.addVariable(contents);
                }
                break;
            default:
                break;
        }
    }
}
