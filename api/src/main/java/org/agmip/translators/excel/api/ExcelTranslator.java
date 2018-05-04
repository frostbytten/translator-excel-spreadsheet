package org.agmip.translators.excel.api;

import org.agmip.ace.*;
import org.agmip.ace.util.AceFunctions;
import org.agmip.translators.excel.api.handler.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.soap.Node;

public class ExcelTranslator {
    public static final Logger LOG = LoggerFactory.getLogger(ExcelTranslator.class);
    private final OPCPackage pkg;
    private final XSSFReader reader;
    private final SharedStringsTable sst;
    private final SAXParser parser;
    private final RootedGraph graph;

    public ExcelTranslator(Path file) throws Exception {
        pkg = OPCPackage.open(file.toFile(), PackageAccess.READ);
        reader = new XSSFReader(pkg);
        sst = reader.getSharedStringsTable();
        parser = SAXParserFactory.newDefaultInstance().newSAXParser();
        this.graph = new RootedGraph();
    }

    public void init() throws Exception {
        try (InputStream wkb = reader.getWorkbookData()) {
            InputSource source = new InputSource(wkb);
            SheetHandler handler = new SheetHandler(this.graph);
            parser.parse(source, handler);
        }
        for (DataNode n: graph.unassigned()) {
            LOG.info("Node with [{}] {}", n.id(), n.name());
            try (InputStream stream = reader.getSheet(n.id())) {
                InputSource source = new InputSource(stream);
                VariableHandler vh = new VariableHandler(n, sst);
                parser.parse(source, vh);
            }
        }
        List<String> temp = new ArrayList<>(50);
        List<String> dups = new ArrayList<>();
        List<String> defined = new ArrayList<>();
        for (DataNode n: graph.unassigned()) {
            for (String v: n.variables()) {
                if (v.equals("date")) continue;
                if (v.contains("time")) continue;
                if (temp.contains(v)) {
                    if (! dups.contains(v)) {
                        dups.add(v);
                    }
                } else {
                    temp.add(v);
                }
            }
        }

        // Assign root to a sheet which contains a root but may not define it
        temp.removeAll(dups);
        for (DataNode n: graph.unassigned()) {
             for (String v: n.variables()) {
                 if (temp.contains(v) && Util.ROOT_FIELDS.contains(v)) {
                     n.root(v);
                 }
             }
        }

        temp.clear();

        for (DataNode n: graph.unassigned()) {
            LOG.info("Node {} has {} potential index(es)", n.name(), numIndexDups(dups, n.variables()));
            try (InputStream stream = reader.getSheet(n.id())) {
                int indexColumns = numIndexDups(dups, n.variables());
                InputSource source = new InputSource(stream);
                FirstPassDataHandler fpdh = new FirstPassDataHandler(n, indexColumns, defined, sst);
                parser.parse(source, fpdh);
                if (n.doesDefine()) {
                    String tempDefine = n.defines().get();
                    String d;
                    if (tempDefine.contains(",")) {
                        d = tempDefine.substring(tempDefine.lastIndexOf(",")+1);
                    } else {
                        d = tempDefine;
                    }
                    defined.add(d);
                    if (Util.ROOT_FIELDS.contains(n.defines().get())) {
                        n.root(n.defines().get());
                    }
                }
            }
        }

        dups.removeAll(defined);

        // Try to define the remaining duplicates
        for (String d: dups) {
            for (DataNode n : graph.unassigned()) {
                if (!n.doesDefine()) {
                    if (n.lastIndex().equals(d)) {
                        // Let's just assume for the sake of expediency
                        LOG.info("{} could define {} - {}", n.name(), d, n.indexes());
                        n.defines(n.lastIndex());
                        defined.add(n.lastIndex());
                        if (Util.ROOT_FIELDS.contains((n.defines().get()))) {
                            n.root(n.defines().get());
                        }
                        break;
                    }
                }
            }
        }
        dups.removeAll(defined);
        for (DataNode n: graph.unassigned()) {
            LOG.info("{} {}", n.name(), n.defines());
        }
        graph.build();
    }

    public AceDataset run() throws Exception {
        AceDataset ds = new AceDataset();
        for(DataNode root: graph.roots()) {
            List<AceComponent> results = step(ds, root, null, new HashMap<>());
            LOG.info("Walk path: {}", root.path());
            switch (root.path()) {
                case "management":
                    for (AceComponent c: results) {
                        ds.addExperiment(c.getRawComponent());
                    }
                    break;
                case "weather":
                    for (AceComponent c: results) {
                        ds.addWeather(c.getRawComponent());
                    }
                    break;
                case "soil":
                    for (AceComponent c: results) {
                        ds.addSoil(c.getRawComponent());
                    }
                    break;
            }
        }
        // Let's try to manually fix the links for the experiments
        Map<String, AceWeather> weatherMap = new HashMap<>();
        Map<String, AceSoil> soilMap = new HashMap<>();
        for(AceWeather w : ds.getWeathers()) {
            String wstID = w.getValueOr("wst_id", "");
            LOG.info("Found weather {}", wstID);
            weatherMap.put(wstID, w);
        }
        for(AceSoil s : ds.getSoils()) {
            String sID = s.getValueOr("soil_id", "");
            LOG.info("Found soil {}", sID);
            soilMap.put(sID, s);
        }
        for(AceExperiment e: ds.getExperiments()) {
            String wstID = e.getValueOr("wst_id", "");
            String sID = e.getValueOr("soil_id", "");
            if (weatherMap.containsKey(wstID)) {
                LOG.info("Looked up and found {}", wstID);
                e.setWeather(weatherMap.get(wstID));
            }
            if (soilMap.containsKey(sID)) {
                LOG.info("Looked up and found {}", sID);
                e.setSoil(soilMap.get(sID));
            }
        }
        return ds;
    }

    private List<AceComponent> step(AceDataset ds, DataNode node, List<AceComponent> translatedRoot, Map<String, List<AceComponent>> refs) throws IOException {
        List<AceComponent> translated = translateNode(node);
        if (refs.isEmpty()) {
            refs = storeReferencesFor(node);
        }
        if (! node.isRoot()) {
            translated = handleChildren(ds, translatedRoot, translated, refs, node);
        }
        for(DataNode c: node.children()) {
            translated = step(ds, c, translated, refs);
        }
        return translated;
    }

    private Map<String, List<AceComponent>> storeReferencesFor(DataNode node) throws IOException {
        Map<String, List<AceComponent>>refs = new HashMap<>();
        for(DataNode ref: node.references()) {
            List<AceComponent> res = translateNode(ref);
            for(AceComponent c : res) {
                String refKey = generateReferenceKey(ref, c);
                if (refs.containsKey(refKey)) {
                    List<AceComponent> lc = refs.get(refKey);
                    lc.add(c);
                } else {
                    List<AceComponent> lc = new ArrayList<>();
                    lc.add(c);
                    refs.put(refKey, lc);
                }
            }
        }
        return refs;
    }

    private List<AceComponent> handleChildren(AceDataset ds, List<AceComponent> roots, List<AceComponent> children, Map<String, List<AceComponent>> refs, DataNode node) throws IOException {
        List<AceComponent> results = new ArrayList<>();
        switch(Util.frontPath(node.path())) {
            case "weather":
                LOG.info("Handling weather child: {}", node.name());
                List<AceWeather> weathers = handleWeather(roots, children, refs, node);
                for (AceWeather weather: weathers) {
                    results.add(new AceComponent(weather.rebuildComponent()));
                }
                break;
            case "soil":
                LOG.info("Handling soil child: {}", node.name());
                List<AceSoil> soils = handleSoil(roots, children, refs, node);
                for (AceSoil soil: soils) {
                    results.add(new AceComponent(soil.rebuildComponent()));
                }
                break;
            default:
                LOG.info("Handling experimental child: {}", node.name());
                List<AceExperiment> exps = handleExperiment(roots, children, refs, node);
                for (AceExperiment exp: exps) {
                    results.add(new AceComponent(exp.rebuildComponent()));
                }
                break;
        }
        return results;
    }

    private List<AceExperiment> handleExperiment(List<AceComponent> roots, List<AceComponent> children, Map<String, List<AceComponent>> refs, DataNode node) throws IOException {
        List<AceExperiment> results = new ArrayList<>();
        List<String> orderedKeys = new ArrayList<>();
        Map<String, AceComponent> mappedRoots = new HashMap<>();
        boolean flatMerge = (node.parent().indexes().equals(node.indexes()));
        LOG.info("Flat merge: {}", flatMerge);
        for(AceComponent root: roots) {
            String k = generateReferenceKey(node.parent(), root);
            if (mappedRoots.containsKey(k)) {
                LOG.error("REFERENCE KEY EXISTS: {}", k);
            } else {
                mappedRoots.put(k, root);
                orderedKeys.add(k);
            }
        }
        for(AceComponent child: children) {
            String k = generateReferenceKey(node.parent(), child);
            if (mappedRoots.containsKey(k)) {
                if (flatMerge) {
                    AceExperiment ex = new AceExperiment(mappedRoots.get(k).getRawComponent());
                    mergeExChildren(child, ex, node);
                    mappedRoots.put(k, new AceComponent(ex.rebuildComponent()));
                } else {
                    mergeComponents(mappedRoots.get(k), child);
                    String newExname = child.getValueOr("exname", "") + "_" + child.getValueOr("trtno", "");
                    LOG.info("NewEX: {}", newExname);
                    if (! newExname.equals("_")) {
                        child.update("exname", newExname, true, true, false);
                    }
                    AceExperiment e = new AceExperiment(child.getRawComponent());
                    mergeExReferences(e, refs, node);
                    e.getId(true);
                    results.add(e);
                }
            } else {
                LOG.error("Child does not have parent reference: {}", k);
            }
        }
        if (results.size() == 0) {
            for(String k: orderedKeys) {
                results.add(new AceExperiment(mappedRoots.get(k).getRawComponent()));
            }
        }
        return results;
    }

    private List<AceSoil> handleSoil(List<AceComponent> roots, List<AceComponent> children, Map<String, List<AceComponent>> refs, DataNode node) throws IOException {
        List<AceSoil> results = new ArrayList<>();
        List<String> orderedKeys = new ArrayList<>();
        Map<String, AceComponent> mappedRoots = new HashMap<>();
        for(AceComponent root: roots) {
            String k = generateReferenceKey(node.parent(), root);
            if (mappedRoots.containsKey(k)) {
                LOG.error("REFERENCE KEY EXISTS: {}", k);
            } else {
                mappedRoots.put(k, root);
                orderedKeys.add(k);
            }
        }
        for(AceComponent child: children) {
            String k = generateReferenceKey(node.parent(), child);
            if (mappedRoots.containsKey(k)) {
                AceSoil s = new AceSoil(mappedRoots.get(k).getRawComponent());
                AceRecordCollection col = s.getSoilLayers();
                col.add(new AceRecord(child.getRawComponent()));
                mappedRoots.put(k, new AceComponent(s.rebuildComponent()));
            }
        }
        for(String k : orderedKeys) {
            results.add(new AceSoil(mappedRoots.get(k).getRawComponent()));
        }
        return results;
    }

    private List<AceWeather> handleWeather(List<AceComponent> roots, List<AceComponent> children, Map<String, List<AceComponent>> refs, DataNode node) throws IOException {
        List<AceWeather> results = new ArrayList<>();
        List<String> orderedKeys = new ArrayList<>();
        Map<String, AceComponent> mappedRoots = new HashMap<>();
        for(AceComponent root: roots) {
            String k = generateReferenceKey(node.parent(), root);
            if (mappedRoots.containsKey(k)) {
                LOG.error("REFERENCE KEY EXISTS: {}", k);
            } else {
                mappedRoots.put(k, root);
                orderedKeys.add(k);
            }
        }
        for(AceComponent child: children) {
            String k = generateReferenceKey(node.parent(), child);
            if (mappedRoots.containsKey(k)) {
                AceWeather w = new AceWeather(mappedRoots.get(k).getRawComponent());
                AceRecordCollection col = w.getDailyWeather();
                col.add(new AceRecord(child.getRawComponent()));
                mappedRoots.put(k, new AceComponent(w.rebuildComponent()));
            }
        }
        for(String k : orderedKeys) {
            results.add(new AceWeather(mappedRoots.get(k).getRawComponent()));
        } 
        return results;
    }

    private void mergeExChildren(AceComponent source, AceExperiment dest, DataNode node) throws IOException {
        switch(node.path()) {
            case "observed":
                LOG.info("Merging observed data");
                AceObservedData obs = dest.getObservedData();
                mergeComponents(source, obs);
                break;
            case "observed@timeSeries":
                LOG.info("Merging observed timeseries data");
                AceRecordCollection col = dest.getObservedData().getTimeseries();
                col.add(new AceRecord(source.getRawComponent()));
                break;
            default:
                break;
        }
    }

    private void mergeExReferences(AceExperiment component, Map<String, List<AceComponent>> refs, DataNode node) throws IOException {
        for(DataNode ref: node.references()) {
            String refKey = generateReferenceKey(ref, component);
            LOG.info("Reference Key Lookup: {} {}", refKey, refs.containsKey(refKey));
            if (refs.containsKey(refKey)) {
                List<AceComponent> references = refs.get(refKey);
                switch(ref.path()) {
                    case "management":
                        for(AceComponent r: references) {
                            mergeComponents(r, component);
                        }
                        break;
                    case "management@events!planting":
                        AceEventCollection currentEvents = component.getEvents().filterByEvent(AceEventType.ACE_PLANTING_EVENT);
                        if (currentEvents.size() == 0) {
                            for(AceComponent r: references) {
                                r.update("event", "planting", true, true, false);
                                if (r.keySet().contains("PDATE")) {
                                    r.update("date", r.getValue("pdate"), true, true, false);
                                }
                                AceEvent evt = new AceEvent(r.getRawComponent());
                                component.getEvents().asList().add(evt);
                            }
                        } else {
                            for(AceEvent evt: currentEvents) {
                                for(AceComponent r: references) {
                                    if (r.keySet().contains("pdate")) {
                                        r.update("date", r.getValue("pdate"), true, true, false);
                                    }
                                    mergeComponents(r, evt);
                                }
                            }
                        }
                        break;
                    case "management@events!irrigation":
                        for(AceComponent r: references) {
                            r.update("event", "irrigation", true, true, false);
                            if (r.keySet().contains("idate")) {
                                r.update("date", r.getValue("idate"), true, true, false);
                            }
                            AceEvent evt = new AceEvent(r.getRawComponent());
                            component.getEvents().asList().add(evt);
                        }
                        break;
                    case "management@events!fertilizer":
                        for(AceComponent r: references) {
                            r.update("event", "fertilizer", true, true, false);
                            if (r.keySet().contains("fedate")) {
                                r.update("date", r.getValue("fedate"), true, true, false);
                            }
                            AceEvent evt = new AceEvent(r.getRawComponent());
                            component.getEvents().asList().add(evt);
                        }
                        break;
                    case "initial_conditions":
                        for(AceComponent r: references) {
                            mergeComponents(r, component);
                        }
                        break;
                    case "initial_conditions@soilLayer":
                        AceRecordCollection col = component.getInitialConditions().getSoilLayers();
                        for(AceComponent r: references) {
                            col.add(new AceRecord(r.getRawComponent()));
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private List<AceComponent> translateNode(DataNode node) throws IOException {
        List<AceComponent> translated = new ArrayList<>();
        try (InputStream stream = reader.getSheet(node.id())) {
            InputSource source = new InputSource(stream);
            TranslationHandler th = new TranslationHandler(node, translated, this.sst);
            this.parser.parse(source, th);
        } catch (InvalidFormatException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return translated;
    }

    private int numIndexDups(List<String> dups, List<String> search) {
        int n = 0;
        for (String s: search) {
            if (dups.contains(s)) {
                n++;
            } else {
                return n;
            }
        }
        return n;
    }

    private boolean matchNode(AceComponent component, DataNode node, Map<String, String> varMap) throws IOException {
        for(String key: Arrays.asList(node.parent().indexes().split(","))) {
            if (! matchComponent(key, component, varMap)) return false;
        }
        return true;
    }

    private boolean matchComponent(String key, AceComponent component, Map<String, String> varMap) throws IOException {
        String cmp = component.getValueOr(key, "");
        if (! cmp.equals(varMap.get(key))) {
            return false;
        }
        return true;
    }

    private void mergeComponents(AceComponent source, AceComponent dest) throws IOException {
        for (String k: source.keySet()) {
            dest.update(k, source.getValue(k), true, true, false);
        }
    }

    private String generateReferenceKey(DataNode node, AceComponent c) throws IOException {
        StringBuilder refKey = new StringBuilder();
        for (String key: Arrays.asList(node.indexes().split(","))) {
            refKey.append(key);
            refKey.append(":");
            refKey.append(c.getValue(key));
            refKey.append(",");
        }
        return refKey.substring(0,refKey.length()-1);
    }

    private void addEvent(AceExperiment e, AceComponent c) throws IOException {
        AceEvent evt = new AceEvent(c.getRawComponent());
        e.getEvents().asList().add(evt);
    }
}
