package org.agmip.translators.excel.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.agmip.ace.AceDataset;
import org.agmip.ace.lookup.LookupPath;
import org.agmip.translators.excel.api.handler.TranslationHandler;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class DataNode {
  private static final Logger LOG = LoggerFactory.getLogger(DataNode.class);
  private String id;
  private String name;
  private Optional<String> rootField;
  private Optional<String> defines;
  private DataNode parent;
  private List<DataNode> references;
  private List<String> linkages;
  private List<DataNode> children;
  private List<String> variables;
  private XSSFReader reader;
  private boolean assigned;
  private String indexes;
  private XMLReader parser;
  private String nodePath;

  public DataNode(String id, String name, XSSFReader reader) {
    this.id = id;
    this.name = name;
    this.reader = reader;
    this.references = new ArrayList<>();
    this.linkages = new ArrayList<>();
    this.children = new ArrayList<>();
    this.variables = new ArrayList<>(50);
    this.rootField = Optional.empty();
    this.defines = Optional.empty();
    this.assigned = false;
    this.nodePath = "";
    try {
      this.parser = XMLReaderFactory.createXMLReader();
    } catch (SAXException e) {
      e.printStackTrace();
    }
  }

  public DataNode(String name) {
    this(null, name, null);
  }

  public DataNode() {
    this(null, null, null);
  }

  public String name() {
    return this.name;
  }

  public String id() {
    return this.id;
  }

  public void addReference(DataNode s) {
    this.references.add(s);
  }

  public void link(String v) {
    for (String l : Arrays.asList((v.split(",")))) {
      if (! this.linkages.contains(l)) {
        this.linkages.add(l);
      }
    }
  }

  public List<String> linkages() {
    return this.linkages;
  }

  public void reverseLinks() {
    Collections.reverse(this.linkages);
  }


  public List<DataNode> references() { return this.references; }

  public void addChild(DataNode c) {
    this.children.add(c);
    c.parent(this);
  }

  public void removeChild(DataNode c) {
    DataNode p = c.parent();
    removeChild(p, c);
  }

  private void removeChild(DataNode parent, DataNode child) {
    if (null == parent) {
      LOG.warn("Cannot remove child from null object");
      return;
    }
    parent.children.remove(child);
    child.parent(null);
  }

  public List<DataNode> children() {
    return this.children;
  }

  public boolean hasChildren() { return ! this.children.isEmpty(); }

  public void parent(DataNode p) { this.parent = p; }

  public DataNode parent() { return this.parent; }

  public void addVariable(String v) {
    String cleaned = Util.standardizeVariable(v);
    this.variables.add(cleaned);
  }

  public void assign() { this.assigned = true; }

  public boolean isAssigned() { return this.assigned; }

  public boolean isRoot() {
    return rootField.isPresent();
  }

  public Optional<String> root() {
    return rootField;
  }

  public void root(String root) { rootField = Optional.of(root); }

  public boolean doesDefine() {
    return defines.isPresent();
  }

  public Optional<String> defines() {
    return defines;
  }

  public void defines(String d) {
    defines = Optional.of(d);
  }

  public List<String> variables() {
    return this.variables;
  }

  public void indexes(String i) { this.indexes = i; }

  public String indexes() { return this.indexes; }

  public String lastIndex() {
    return Util.lastEntry(this.indexes);
  }

  public void setPaths() throws Exception {
    this.determinePaths();
    for(DataNode ref : this.references) {
      ref.determinePaths();
    }
    for(DataNode c: this.children) {
      c.setPaths();
    }
  }

  public void display(int lvl) {
    StringBuilder out = new StringBuilder();
    for (int i=0; i < lvl; i++) {
      out.append("-");
    }
    if (lvl > 0) {
      out.append(">");
    }
    out.append(" ");
    out.append(this.name());
    if (null != this.parent()) {
      out.append("(");
      out.append(this.parent().name());
      out.append(")");
    }
    if (lvl == 0) {
      out.append(" *");
      out.append(this.root().get());
      out.append("*");
    }
    if (this.references.size() > 0) {
      out.append("[");
      for (DataNode n: this.references) {
        out.append(" ");
        out.append(n.name());
        out.append(" ");
      }
      out.append("]");
    }

    LOG.info(out.toString());
    for (DataNode n : this.children) {
      n.display(lvl+1);
    }
  }

  private void determinePaths() throws Exception {
    if (! this.nodePath.equals("")) {
      return;
    }
    List<String> determinator = new ArrayList<>();
    for(String var: this.variables()) {
      if (Util.ROOT_FIELDS.contains(var) && ! this.isRoot()) {
        //LOG.info("Skipping root var: {}", var);
      } else if(this.linkages().contains(var)) {
        //LOG.info("Skipping reference var: {} ", var);
      } else {
        String lookup = LookupPath.INSTANCE.getPath(var);
//                LOG.info("{} !! {}", var, lookup);
        if (null != lookup) {
          if (lookup.startsWith(",")) {
            lookup = lookup.substring(1);
          } else if (lookup.equals((""))) {
            lookup = "management";
          }
          if (! this.references().contains(var)) {
            if (var.endsWith("dat") || var.endsWith("date")) {
            } else {
              LOG.info("Wanting to add {} because of {}", lookup, var);
              if (!determinator.contains(lookup)) {
                LOG.info("Adding {} because of {}", lookup, var);
                determinator.add(lookup);
              }
            }
          }
        }
      }
    }
    switch(determinator.size()) {
      case 0:
        LOG.info("{} - Undetermined data", this.name());
        break;
      case 1:
        LOG.info("{} - {}", this.name(), determinator.get(0));
        this.nodePath = determinator.get(0);
        break;
      default:
        LOG.info("{} - Too many different types of data: ({})", this.name(), determinator);
        break;
    }
  }

  public String path() {
    return this.nodePath;
  }
}
