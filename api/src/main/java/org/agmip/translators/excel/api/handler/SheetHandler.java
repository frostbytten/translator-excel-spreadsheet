package org.agmip.translators.excel.api.handler;

import java.io.IOException;
import java.io.InputStream;

import org.agmip.translators.excel.api.DataNode;
import org.agmip.translators.excel.api.RootedGraph;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class SheetHandler extends DefaultHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SheetHandler.class);
  private final SharedStringsTable sst;
  private final XSSFReader reader;
  private final XMLReader parser;
  private final RootedGraph graph;

  public SheetHandler(RootedGraph graph, SharedStringsTable sst, XSSFReader reader) throws SAXException {
    this.graph = graph;
    this.sst = sst;
    this.reader = reader;
    this.parser = XMLReaderFactory.createXMLReader();
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (qName.equals("sheet")) {
      String id = attributes.getValue("r:id");
      String name = attributes.getValue("name");
      if (name.startsWith("DOC_")) return;
      DataNode node = new DataNode(id, name, reader);
      graph.addNode(node);
    }
  }
}
