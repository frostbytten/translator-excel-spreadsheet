package org.agmip.translators.excel.impl.sample;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.agmip.translators.excel.api.ExcelModel;
import org.agmip.ace.AceDataset;
import org.agmip.ace.io.AceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
  public static final Logger LOG = LoggerFactory.getLogger(Main.class);
  public static void main(String[] argv)  {
	  
	
	System.out.println(" I am felipe");
	
	if (argv.length==0) {
		System.err.println("No args passed");
		return;
	}
    Path file = Paths.get(argv[0]);
    Path out  = Paths.get(argv[1]);
    ExcelModel model;
	try {
		model = new ExcelModel(file);
		model.init();
	    AceDataset ds = model.run();
	    LOG.info("Writing file: {}", out.toFile());
	    AceGenerator.generateACEB(out.toFile(), ds);
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    
  }
}
