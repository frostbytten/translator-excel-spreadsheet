package org.agmip.translators.excel.api.handler;

import org.agmip.ace.AceComponent;
import org.agmip.ace.AceDataset;
import org.agmip.translators.excel.api.DataNode;
import org.agmip.translators.excel.api.Util;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class TranslationHandler extends DefaultHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TranslationHandler.class);
    private final SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd");;
    private final SharedStringsTable sst;
    private List<AceComponent> ace;
    private Map<String, String> map;
    private DataNode node;

    private boolean skippedHeader;
    private boolean nextIsString;
    private boolean inlineString;
    private boolean stopProcessing = false;
    private int currentRow;
    private int currentCol;
    private String contents;
    private AceComponent currentTranslation;

    public TranslationHandler(DataNode node, List<AceComponent> ace, SharedStringsTable sst) throws Exception {
        this.sst = sst;
        this.ace = ace;
        this.map = map;
        this.node = node;

        this.skippedHeader = false;
        this.currentRow = 0;
    }

    @Override
    public void startDocument() throws SAXException {
        this.skippedHeader = false;
        this.currentRow = 0;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (stopProcessing) return;
        switch (qName) {
            case "row":
                skippedHeader = true;
                try {
                    this.currentTranslation = new AceComponent();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case "c":
                if (skippedHeader) {
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
        if (! stopProcessing) contents += new String(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (stopProcessing) return;
        if (nextIsString) {
            int idx = Integer.parseInt(contents);
            contents = new XSSFRichTextString(sst.getEntryAt(idx)).toString();
            nextIsString = false;
        }
        contents = Util.standardizeVariable(contents);
        switch(qName) {
            case "row":
                currentCol = 0;
                if (currentRow == 0) {
                    currentRow++;
                    return;
                }
                this.ace.add(this.currentTranslation);
                currentRow++;
                break;
            case "v":
                if (currentRow == 0) return;
                //Do something with the column data
                // Remember to do something with the DAT/DATE stuff
                String currentVar = this.node.variables().get(currentCol);
                if (currentVar.endsWith("date") || currentVar.endsWith("dat")) {
                    Calendar cal = DateUtil.getJavaCalendar(Double.valueOf(contents));
                    contents = isoFmt.format(cal.getTime());
                }
                try {
                    this.currentTranslation.update(currentVar, contents, true, true, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                currentCol++;
                break;
            default:
                break;
        }
    }
}
