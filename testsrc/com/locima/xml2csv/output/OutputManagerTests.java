package com.locima.xml2csv.output;

import static com.locima.xml2csv.TestHelpers.createFile;
import static com.locima.xml2csv.TestHelpers.assertCsvEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OutputManagerTests {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Rule
	public TemporaryFolder testOutputDir = new TemporaryFolder();

	private Map<String, List<String>> addOMConfig(Map<String, List<String>> map, String mappingName, String... values) {
		Map<String, List<String>> mapToAddTo = map == null ? new HashMap<String, List<String>>() : map;
		List<String> s = Arrays.asList(values);
		mapToAddTo.put(mappingName, s);
		return mapToAddTo;
	}

	private OutputManager createTempOutputManager() throws IOException, OutputManagerException {
		OutputManager om = new OutputManager();
		File s = this.testOutputDir.newFolder();
		om.setDirectory(s.getAbsolutePath());
		return om;
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAppendOutput() throws Exception {
		IOutputManager om = new OutputManager();
		File testOutputDir = this.testOutputDir.newFolder();
		om.setDirectory(testOutputDir.getAbsolutePath());
		Map<String, List<String>> config = addOMConfig(null, "test", "col1", "col2", "col3");
		addOMConfig(config, "test2", "colA", "colB", "colC");
		om.createFiles(config, false);
		om.writeRecords("test", Arrays.asList(new String[] { "1", "2", "3" }));
		om.writeRecords("test2", Arrays.asList(new String[] { "A", "B", "C" }));
		om.close();
		om.createFiles(config, true);
		om.writeRecords("test", Arrays.asList(new String[] { "1", "2", "3" }));
		om.writeRecords("test2", Arrays.asList(new String[] { "A", "B", "C" }));
		om.close();
		
		assertCsvEquals("OutputManagerAppendTest1.csv", testOutputDir, "test.csv");
		assertCsvEquals("OutputManagerAppendTest2.csv", testOutputDir, "test2.csv");
	}

	@Test
	public void testClose() throws IOException, OutputManagerException {
		OutputManager om = createTempOutputManager();
		Map<String, List<String>> outputConfiguration = new LinkedHashMap<String, List<String>>();
		om.createFiles(outputConfiguration, false);
		om.close();
	}

	@Test
	public void testCreateFiles() throws IOException, OutputManagerException {
		OutputManager om = createTempOutputManager();
		Map<String, List<String>> config = addOMConfig(null, "test", "col1", "col2", "col3");
		addOMConfig(config, "test2", "colA", "colB", "colC");
		om.createFiles(config, false);
		om.close();
	}

	@Test
	public void testEscape() {
		String[] input = new String[] { null, "a", "", "\"", "a\"b", "," };
		String[] expected = new String[] { null, "a", "", "\"\"\"\"", "\"a\"\"b\"", "\",\"" };
		for (int i = 0; i < input.length; i++) {
			assertEquals(expected[i], OutputManager.escapeForCsv(input[i]));
		}
	}

	@Test
	public void testWriteRecords() throws Exception {
		IOutputManager om = createTempOutputManager();
		Map<String, List<String>> config = addOMConfig(null, "test", "col1", "col2", "col3");
		addOMConfig(config, "test2", "colA", "colB", "colC");
		om.createFiles(config, false);
		om.writeRecords("test", Arrays.asList(new String[] { "1", "2", "3" }));
		om.writeRecords("test2", Arrays.asList(new String[] { "A", "B", "C" }));
		om.close();
	}

}
