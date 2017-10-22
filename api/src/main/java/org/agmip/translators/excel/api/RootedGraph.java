package org.agmip.translators.excel.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RootedGraph {
  private static final Logger LOG = LoggerFactory.getLogger(RootedGraph.class);
  private List<DataNode> roots;
  private List<DataNode> unassigned;
  private List<DataNode> temp;

  public RootedGraph() {
    roots = new ArrayList<>();
    unassigned = new CopyOnWriteArrayList<>();
    temp = new ArrayList<>();
  }

  public List<DataNode> roots() {
    return this.roots;
  }

  public void addNode(DataNode node) {
    this.unassigned.add(node);
  }

  public List<DataNode> unassigned() {
    return this.unassigned;
  }

  public void build() throws Exception {
    assignChildOnly();
    checkpoint();
    assignReferences();
    checkpoint();
    assignRoots();
    checkpoint();
    assignNestedChildren();
    checkpoint();
    reverseAssignment();
    clearAssigned();
    for(DataNode n: unassigned) {
      LOG.info("Still unassigned to anything: {}", n.name());
    }
    //Let's look at this thing!!!
    for (DataNode n : this.roots) {
      n.display(0);
      n.setPaths();
    }
  }

  private boolean preflight(DataNode base, DataNode check) {
    return preflight(base, check, true);
  }

  private boolean preflight(DataNode base, DataNode check, boolean checkBase) {
    if (base == check) return false;
    if (!base.doesDefine()) return false;
    if (! checkBase) {
      return !check.isAssigned();
    } else {
      return ! (check.isAssigned() || base.isAssigned());
    }
  }

  private void checkpoint() {
    for (DataNode n: this.unassigned) {
      if (! n.isAssigned()) {
        LOG.info("Checkpoint: {} is available", n.name());
      }
    }
  }

  private void assignReferences() {
    for (DataNode base: this.unassigned) {
      for (DataNode check: this.unassigned) {
        if (!preflight(base, check)) continue;
        if (check.variables().contains(base.lastIndex()) && ! Util.ROOT_FIELDS.contains(base.lastIndex())) {
          LOG.info("{} references {}", check.name(), base.name());
          check.addReference(base);
          check.link(base.lastIndex());
          base.assign();
        }
      }
    }
  }

  private void assignChildOnly() {
    for (DataNode base: this.unassigned) {
      for (DataNode check: this.unassigned) {
        if (!preflight(base, check)) continue;
        String currentDefinition = base.defines().get();
        if (check.indexes().equals(currentDefinition)) {
          LOG.info("ChildOnly: {} is a child of {}", check.name(), base.name());
          base.addChild(check);
          check.link(currentDefinition);
          check.assign();
        }
      }
    }
  }

  private void assignRoots() {
    for (DataNode n: this.unassigned) {
      if (! n.doesDefine()) continue;
      String currentDefinition = n.defines().get();
      if (Util.ROOT_FIELDS.contains(currentDefinition)) {
        LOG.info("Roots: {} is a root", n.name());
        this.roots.add(n);
        n.assign();
      }
    }
  }

  private void assignNestedChildren() {
    for (DataNode base: this.unassigned) {
      for (DataNode check: this.unassigned) {
        if (!preflight(base, check, false)) continue;
        String currentDefinition = base.defines().get();
        LOG.info("{}: {}", base.name(), currentDefinition);
        if (check.indexes().contains(",")) {
          String headIndex = check.indexes().substring(0, check.indexes().lastIndexOf(","));
          LOG.info("Looking for index: {}", headIndex);
          if (headIndex.equals(currentDefinition)) {
            LOG.info("Nested child found: {} is a child of {}", check.name(), base.name());
            base.addChild(check);
            check.link(headIndex);
            check.assign();
          }
        }
      }
    }
  }

  private void reverseAssignment() {
    for (DataNode n: this.unassigned) {
      if (! n.linkages().isEmpty()) {
        n.reverseLinks();
      }
    }
  }

  private void clearAssigned() {
    for (DataNode n: this.unassigned) {
      if (n.isAssigned()) {
        this.unassigned.remove(n);
      }
    }
  }
}
