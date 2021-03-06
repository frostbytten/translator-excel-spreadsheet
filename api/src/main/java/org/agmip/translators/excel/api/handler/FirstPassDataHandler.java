package org.agmip.translators.excel.api.handler;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.agmip.translators.excel.api.DataNode;
import org.agmip.translators.excel.api.Util;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class FirstPassDataHandler extends DefaultHandler {
  private static final Logger LOG = LoggerFactory.getLogger(FirstPassDataHandler.class);
  private final SharedStringsTable sst;
  private final XSSFReader reader;
  private final XMLReader parser;
  private boolean finished = false;
  private boolean foundRow = false;
  private boolean duplicateDataFound = false;
  private boolean nextIsString;
  private boolean inlineString;
  private int indexColumns;
  private int rowNum = 0;
  private int colNum = 0;
  private DataNode node;
  private String contents;
  private StringBuilder temp;
  private StringBuilder cmp;
  private StringBuilder defines;
  private List<String> defined;

  public FirstPassDataHandler(DataNode node, int indexColumns, List<String> defined, SharedStringsTable sst, XSSFReader reader) throws SAXException {
    this.node = node;
    this.indexColumns = indexColumns;
    this.defined = defined;
    this.sst = sst;
    this.reader = reader;
    this.parser = XMLReaderFactory.createXMLReader();
    this.temp = new StringBuilder();
    this.cmp = new StringBuilder();
    this.defines = new StringBuilder();
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
        // Do comparitor here
        colNum = 0;
        rowNum++;
        if (temp.length() > 0) {
          if (defined.contains(defines.toString())) {
            LOG.info("{} already defined", defines.toString());
            node.indexes(defines.toString());
            finished = true;
            return;
          }
          temp.append("|");
          if (cmp.indexOf(temp.toString()) == -1) {
            cmp.append(temp.toString());
            temp = new StringBuilder();
          } else {
            LOG.info("Duplicate found: {} {}", defines, temp.toString());
            node.indexes(defines.toString());
            finished = true;
          }
        }
        break;
      case "v":
        if (rowNum == 0 && colNum < indexColumns) {
          if (defines.length() == 0) {
            defines.append(contents);
          } else {
            defines.append(",").append(contents);
          }
        } else {
          if (colNum < indexColumns) {
            if (indexColumns == 1) {
              temp.append(contents);
            } else {
              temp.append(contents).append(",");
            }
          }
        }
        colNum++;
        break;
      case "sheetData":
        LOG.info("{} defines {}", node.name(), defines.toString());
        defined.add(defines.toString());
        node.defines(defines.toString());
        node.indexes(node.defines().get());
        break;
      default:
        break;
    }
  }
}
