package org.agmip.translators.excel.api.handler;

import org.agmip.translators.excel.api.DataNode;
import org.agmip.translators.excel.api.RootedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class SheetHandler extends DefaultHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SheetHandler.class);
  private final RootedGraph graph;

  public SheetHandler(RootedGraph graph) throws SAXException {
    this.graph = graph;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (qName.equals("sheet")) {
      String id = attributes.getValue("r:id");
      String name = attributes.getValue("name");
      if (name.startsWith("DOC_")) return;
      DataNode node = new DataNode(id, name);
      graph.addNode(node);
    }
  }
}
