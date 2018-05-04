package org.agmip.translators.excel.api.handler;

import java.util.List;

import org.agmip.translators.excel.api.DataNode;
import org.agmip.translators.excel.api.RootedGraph;
import org.agmip.translators.excel.api.Util;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class DataHandler extends DefaultHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DataHandler.class);
    private final SharedStringsTable sst;
    private boolean finished = false;
    private boolean foundRow = false;
    private boolean duplicateDataFound = false;
    private boolean nextIsString;
    private boolean inlineString;
    private int indexColumns;
    private int rowNum = 0;
    private int colNum = 0;
    private String contents;

    public DataHandler(int indexColumns, SharedStringsTable sst) throws SAXException {
        this.indexColumns = indexColumns;
        this.sst = sst;
    }

    @Override
    public void startDocument() throws SAXException {
        this.rowNum = 0;
        this.colNum = 0;
        this.foundRow = false;
        this.finished = false;
        this.duplicateDataFound = false;
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
                colNum = 0;
                rowNum++;
                if (rowNum > 10) {
                    finished = true;
                }
                LOG.info("Processing row {}", rowNum);
                break;
            case "v":
                colNum++;
                if (colNum < indexColumns) {
                    LOG.info("--Value found: {}", contents);
                }
                break;
            default:
                break;
        }
    }
}
