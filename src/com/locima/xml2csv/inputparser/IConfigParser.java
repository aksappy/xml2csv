package com.locima.xml2csv.inputparser;

import java.io.File;
import java.util.List;

import com.locima.xml2csv.XMLException;

/**
 * Used for classes that can read input configuration files.
 */
public interface IConfigParser {

	/**
	 * Load a set of input configuration files.
	 * @param inputConfigFiles the (possibly empty) list of configuration files to read.
	 */
	void load(List<File> inputConfigFiles) throws FileParserException, XMLException;
	
	/**
	 * Retrieves the mappings read from the configuration files.
	 * @return a (possibly empty) set of mappings that instruct the XML extractor how to map from XML to CSV output.
	 */
	MappingsSet getMappings();

}
