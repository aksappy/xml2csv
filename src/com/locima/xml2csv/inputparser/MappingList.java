package com.locima.xml2csv.inputparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.locima.xml2csv.ArgumentException;
import com.locima.xml2csv.ArgumentNullException;
import com.locima.xml2csv.SaxonProcessorManager;
import com.locima.xml2csv.StringUtil;
import com.locima.xml2csv.Tuple;
import com.locima.xml2csv.XMLException;
import com.locima.xml2csv.extractor.DataExtractorException;

/**
 * Models an ordered list of mappings of column outputName to XPath expression.
 */
public class MappingList extends ArrayList<IMapping> implements IMappingContainer {

	private static final Logger LOG = LoggerFactory.getLogger(MappingList.class);

	private static final long serialVersionUID = -3781997484476001198L;

	private Map<String, String> defaultNamespaceMappings;

	private XPathExecutable mappingRoot;

	private int maxInstanceCount;

	private String outputName;

	private Processor saxonProcessor;

	/**
	 * Calls {@link MappingList#NameToXPathMappings(Map)} with an empty map.
	 */
	public MappingList() {
		this(new HashMap<String, String>());
	}

	/**
	 * Initialises a Saxon processor, using the supplied map of namespace prefix to URI mappings.
	 *
	 * @param defaultPrefixUriMap a (possibly empty, but must not be null) map of prefix to URI mappings
	 */
	public MappingList(Map<String, String> defaultPrefixUriMap) {
		if (defaultPrefixUriMap == null) {
			throw new IllegalArgumentException("defaultPrefixUriMap");
		}
		this.saxonProcessor = SaxonProcessorManager.getProcessor();
		this.defaultNamespaceMappings = defaultPrefixUriMap;
	}

	/**
	 * Given a default namespace URI and an XPath expression (that uses only the default namespace), create a compiled version of the XPath
	 * expression.
	 *
	 * @param defaultNamespace the default namespace URI.
	 * @param xPathExpression the XPath expression to compile
	 * @return A compiled XPath expression.
	 * @throws XMLException If there was problem compiling the expression (for example, if the XPath is invalid).
	 */
	private XPathExecutable createXPathExecutable(String defaultNamespace, String xPathExpression) throws XMLException {
		// Need to construct a new compiler because the set of namespaces is (potentially) unique to the expression.
		// We could cache a set of compilers, but I doubt it's worth it.
		XPathCompiler xPathCompiler = this.saxonProcessor.newXPathCompiler();
		for (Map.Entry<String, String> entry : this.defaultNamespaceMappings.entrySet()) {
			String prefix = entry.getKey();
			String uri = entry.getValue();
			xPathCompiler.declareNamespace(prefix, uri);
			if (prefix.equals(defaultNamespace)) {
				LOG.trace("Allocating default namespace prefix {} to URI {}", prefix, uri);
				xPathCompiler.declareNamespace(XMLConstants.DEFAULT_NS_PREFIX, uri);
			}
		}

		try {
			XPathExecutable xPath = xPathCompiler.compile(xPathExpression);
			return xPath;
		} catch (SaxonApiException e) {
			throw new XMLException(e, "Unable to compile invalid XPath: %s", xPathExpression);
		}
	}

	@Override
	public List<String> evaluate(XdmNode rootNode, boolean trimWhitespace) throws DataExtractorException {
		List<List<String>> outputLines = evaluateToRecordList(rootNode, trimWhitespace);
		List<String> outputLine = new ArrayList<String>();
		for (List<String> line : outputLines) {
			outputLine.addAll(line);
		}
		return outputLine;
	}

	/**
	 * Evaluates a nested mapping, appending the results to the output line passed.
	 *
	 * @param node the node from which all mappings will be based on.
	 * @param outputLine the existing output line that will be appended to.
	 * @param trimWhitespace if true, then leading and trailing whitespace will be removed from all data values.
	 * @throws DataExtractorException if an error occurred whilst extracting data (typically this would be caused by bad XPath, or XPath invalid from
	 *             the <code>mappingRoot</code> specified).
	 */
	private void evaluate(XdmNode node, List<String> outputLine, boolean trimWhitespace) throws DataExtractorException {
		for (IMapping mapping : this) {
			outputLine.addAll(mapping.evaluate(node, trimWhitespace));
		}
	}

	@Override
	public List<List<String>> evaluateToRecordList(XdmNode rootNode, boolean trimWhitespace) throws DataExtractorException {
		/**
		 * Execute this mapping for the passed XML document by: 1. Getting the mapping root(s) of the mapping. 2. If there isn't a mapping root, use
		 * the document element (one root). 3. Execute this mapping for each of the roots. 4. Each execution results in a single call to om (one CSV
		 * line).
		 */
		List<List<String>> outputLines = new ArrayList<List<String>>();
		XPathExecutable rootXPath = getMappingRoots();
		XPathSelector rootIterator;
		int instanceCount = 0;
		if (rootXPath != null) {
			rootIterator = rootXPath.load();
			try {
				rootIterator.setContextItem(rootNode);
			} catch (SaxonApiException e) {
				throw new DataExtractorException(e, "Error evaluating XPath %s", rootXPath);
			}
			for (XdmItem item : rootIterator) {
				if (item instanceof XdmNode) {
					List<String> outputLine = new ArrayList<String>();
					evaluate((XdmNode) item, outputLine, trimWhitespace);
					outputLines.add(outputLine);
					instanceCount++;
				} else {
					LOG.warn("Expected XdmNode, got {}", item.getClass().getName());
				}
			}
		} else {
			List<String> outputLine = new ArrayList<String>();
			evaluate(rootNode, outputLine, trimWhitespace);
			instanceCount++;
			outputLines.add(outputLine);
		}

		// Add any blanks where maxInstanceCount is more than valuesSize
		if (instanceCount < this.maxInstanceCount) {
			LOG.trace("Adding {} blank fields to make up to {}", this.maxInstanceCount - instanceCount, this.maxInstanceCount);
			for (int i = instanceCount; i < instanceCount; i++) {
				List<String> emptyValues = new ArrayList<String>();
				outputLines.add(emptyValues);
			}
		}
		this.maxInstanceCount = Math.max(this.maxInstanceCount, instanceCount);

		LOG.trace("Completed all mappings against documents");
		return outputLines;
	}

	@Override
	public List<String> getColumnNames() {
		List<String> colNames = new ArrayList<String>();
		for (IMapping mapping : this) {
			colNames.addAll(mapping.getColumnNames());
		}
		return colNames;
	}

	/**
	 * Retrieves the mapping root expression that, when evaluated, will return all the XML node that should be used to extract data from.
	 *
	 * @return a mapping root expression, or null if these mappings should be executed against whatever the parents root was (in the case of a
	 *         non-nested MappingList, this will be the document element of the XML document).
	 */
	public XPathExecutable getMappingRoots() {
		return this.mappingRoot;
	}

	@Override
	public Tuple<String, List<String>> getMappingsHeaders() {
		return new Tuple<String, List<String>>(this.outputName, getColumnNames());
	}

	@Override
	public int getMaxInstanceCount() {
		return this.maxInstanceCount;
	}

	/**
	 * Retrieves the output outputName of this set of mappings.
	 *
	 * @return the outputName of this set of mappings. Will never be null or the empty string.
	 */
	@Override
	public String getOutputName() {
		return this.outputName;
	}

	/**
	 * Stores a new column definition in this set of mappings.
	 *
	 * @param colName the outputName of the column, must a string of length > 0.
	 * @param defaultNamespace the default namespace URI.
	 * @param xPathExpression the XPath expression to compile. Must not be null.
	 * @throws XMLException If there was problem compiling the expression (for example, if the XPath is invalid).
	 */
	public void put(String colName, String defaultNamespace, String xPathExpression) throws XMLException {
		if (StringUtil.isNullOrEmpty(colName)) {
			throw new ArgumentException("colName", StringUtil.NULL_OR_EMPTY_MESSAGE);
		}
		if (StringUtil.isNullOrEmpty(xPathExpression)) {
			throw new ArgumentException("xPathExpression", StringUtil.NULL_OR_EMPTY_MESSAGE);
		}
		XPathExecutable xPath = createXPathExecutable(defaultNamespace, xPathExpression);
		this.add(new Mapping(colName, new XPathValue(xPathExpression, xPath)));
	}

	/**
	 * Sets the query that returns the XML node(s) from which all the mappings will be based.
	 *
	 * @param defaultNamespace the default namespace URI.
	 * @param mappingRootXPathExpression the XPath expression that will return one or more nodes. All other XPath expressions within this mapping will
	 *            be executed from the context of the returned node(s). Multiple nodes means multiple lines of output.
	 * @throws XMLException If there was problem compiling the expression (for example, if the XPath is invalid).
	 */
	public void setMappingRoot(String defaultNamespace, String mappingRootXPathExpression) throws XMLException {
		this.mappingRoot = createXPathExecutable(defaultNamespace, mappingRootXPathExpression);
	}

	/**
	 * Sets the output outputName of this mapping.
	 *
	 * @param newName the new outputName of the mapping. Must not be null or the empty string.
	 */
	public void setName(String newName) {
		if (newName == null) {
			throw new ArgumentNullException("newName");
		}
		if (newName.length() == 0) {
			throw new ArgumentException("newName", "must have a length >0");
		}
		this.outputName = newName;
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder("MappingList(");
		sb.append(this.getOutputName());
		sb.append(")[");
		for (IMapping mapping : this) {
			sb.append(mapping.toString());
		}
		sb.append("]");
		return sb.toString();
	}

}