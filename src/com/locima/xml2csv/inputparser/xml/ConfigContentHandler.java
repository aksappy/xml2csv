package com.locima.xml2csv.inputparser.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.PatternSyntaxException;

import javax.xml.XMLConstants;
import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.locima.xml2csv.ArgumentNullException;
import com.locima.xml2csv.XMLException;
import com.locima.xml2csv.configuration.IMapping;
import com.locima.xml2csv.configuration.IMappingContainer;
import com.locima.xml2csv.configuration.IValueMapping;
import com.locima.xml2csv.configuration.Mapping;
import com.locima.xml2csv.configuration.MappingConfiguration;
import com.locima.xml2csv.configuration.MappingList;
import com.locima.xml2csv.configuration.MultiValueBehaviour;
import com.locima.xml2csv.configuration.NameFormat;
import com.locima.xml2csv.configuration.PivotMapping;
import com.locima.xml2csv.configuration.XPathValue;
import com.locima.xml2csv.configuration.filter.FileNameInputFilter;
import com.locima.xml2csv.configuration.filter.IInputFilter;
import com.locima.xml2csv.configuration.filter.XPathInputFilter;
import com.locima.xml2csv.inputparser.FileParserException;
import com.locima.xml2csv.util.StringUtil;
import com.locima.xml2csv.util.XmlUtil;

/**
 * The SAX Content Handler for input XML files.
 */
// CHECKSTYLE:OFF Class fan-out complexity is a concern here, as is the size of the source, but I can't think of a better way right now.
public class ConfigContentHandler extends DefaultHandler {
	// CHECKSTYLE:ON

	/**
	 * All of the valid element names that will be processed. This is used to make the {@link #startElement(String, String, String, Attributes)} code
	 * a bit more elegant using a switch statement.
	 */
	private static enum ElementNames {
		FileNameInputFilter, Filters, Mapping, MappingConfiguration, MappingList, PivotMapping, XPathInputFilter
	}

	private static final String CUSTOM_NAME_FORMAT_ATTR = "customNameFormat";

	private static final String FILENAME_FILTER_MATCH_LOCAL_ATTR = "matchLocalFileNameOnly";
	private static final String GROUP_NUMBER_ATTR = "group";
	private static final String KEY_XPATH_ATTR = "keyXPath";
	private static final String KVPAIR_ROOT_XPATH_ATTR = "kvPairRoot";
	private static final Logger LOG = LoggerFactory.getLogger(ConfigContentHandler.class);
	private static final String MAPPING_NAMESPACE = "http://locima.com/xml2csv/MappingConfiguration";
	private static final String MAPPING_ROOT_ATTR = "mappingRoot";
	private static final String MAX_VALUES_ATTR = "minOccurs";
	private static final String MIN_VALUES_ATTR = "maxOccurs";
	private static final String MULTI_VALUE_BEHAVIOUR_ATTR = "behaviour";
	private static final String NAME_ATTR = "name";
	private static final String NAME_FORMAT_ATTR = "nameFormat";
	private static final String VALUE_XPATH_ATTR = "valueXPath";
	private static final String XPATH_ATTR = "xPath";

	private int currentGroupNumber;

	private Locator documentLocator;

	private Stack<IInputFilter> inputFilterStack;

	private MappingConfiguration mappingConfiguration;

	private Stack<MappingList> mappingListStack;

	/**
	 * Adds a filter to either the mapping configuration (if a top level filter) or the current parent filter (from {@link #inputFilterStack}.
	 *
	 * @param filter the filter to add, must not be null.
	 */
	private void addFilter(IInputFilter filter) {
		if (filter == null) {
			throw new ArgumentNullException("filter");
		}
		if (this.inputFilterStack.isEmpty()) {
			this.mappingConfiguration.addInputFilter(filter);
		} else {
			this.inputFilterStack.peek().addNestedFilter(filter);
		}
		this.inputFilterStack.push(filter);
	}

	/**
	 * Adds a field mapping to the current MappingList instance being defined.
	 *
	 * @param name the name of the field.
	 * @param xPath the XPath that should be executed to get the value of the field.
	 * @param predefinedNameFormat the name of one of the built-in styles (see {@link NameFormat} public members.
	 * @param groupNumber the group number that applies to this mapping.
	 * @param bespokeNameFormatFormat a bespoke style to use for this mapping.
	 * @param multiValueBehaviour defines what should happen when multiple values are found for a single evaluation on an element.
	 * @param minValueCount the minimum number of values that this mapping should output for a single evaluation on an element.
	 * @param maxValueCount the maximum number of values that this mapping should output for a single evaluation on an element.
	 * @throws SAXException if an error occurs while parsing the XPath expression found (will wrap {@link XMLException}.
	 */
	// CHECKSTYLE:OFF
	private void addMapping(String name, String xPath, String predefinedNameFormat, String bespokeNameFormatFormat, Integer groupNumber,
					String multiValueBehaviour, Integer minValueCount, Integer maxValueCount) throws SAXException {
		// CHECKSTYLE:ON
		MappingList current = this.mappingListStack.peek();
		NameFormat nameFormat = NameFormat.parse(predefinedNameFormat, bespokeNameFormatFormat, NameFormat.NO_COUNTS);
		String fieldName;
		if (StringUtil.isNullOrEmpty(name)) {
			LOG.debug("No name was specified for mapping, so XPath value is used instead {}", xPath);
			fieldName = xPath.replace('/', '_');
		} else {
			fieldName = name;
		}
		XPathValue compiledXPath;
		String[] availableVariables = getPreviousSiblingNames();
		try {
			compiledXPath = XmlUtil.createXPathValue(this.mappingConfiguration.getNamespaceMap(), xPath, availableVariables);
		} catch (XMLException e) {
			throw getException(e, "Unable to add field %s as there was a problem with the XPath value \"%s\"", name, xPath);
		}

		Mapping mapping = new Mapping();
		mapping.setParent(current);
		mapping.setName(fieldName);
		mapping.setNameFormat(nameFormat);
		mapping.setGroupNumber(groupNumber == null ? this.currentGroupNumber : groupNumber);
		mapping.setMultiValueBehaviour(MultiValueBehaviour.parse(multiValueBehaviour, this.mappingConfiguration.getDefaultMultiValueBehaviour()));
		mapping.setValueXPath(compiledXPath);
		mapping.setMinValueCount(minValueCount == null ? 0 : minValueCount);
		mapping.setMaxValueCount(maxValueCount == null ? 0 : maxValueCount);
		current.add(mapping);
	}

	/**
	 * Configures the inline behaviour (the instance of {@link MappingConfiguration} is already initialised on {@link #startDocument()}.
	 *
	 * @param predefinedNameFormat the name of a predefined name format (see static {@link NameFormat} instances).
	 * @param multiValueBehaviour the inline behaviour to observe, by default, for all child mappings.
	 */
	private void addMappingConfiguration(String predefinedNameFormat, String multiValueBehaviour) {
		this.mappingConfiguration.setDefaultMultiValueBehaviour(MultiValueBehaviour.parse(multiValueBehaviour, MultiValueBehaviour.LAZY));
		this.mappingConfiguration.setDefaultNameFormat(NameFormat.parse(predefinedNameFormat, null, NameFormat.NO_COUNTS));
		this.mappingListStack = new Stack<MappingList>();
	}

	/**
	 * Initialises a new MappingList object based on a Mapping element.
	 *
	 * @param mappingRoot The XPath expression that identifies the "root" elements for the mapping.
	 * @param outputName The name of the output that this set of mappings should be written to.
	 * @param predefinedNameFormat the name of one of the built-in styles (see {@link NameFormat} public members.
	 * @param multiValueBehaviour defines what should happen when multiple values are found for a single evaluation for this mapping.
	 * @param groupNumber the group number that applies to this mapping.
	 * @param minValueCount the minimum number of values that this mapping should output for a single evaluation on an element.
	 * @param maxValueCount the maximum number of values that this mapping should output for a single evaluation on an element.
	 * @throws SAXException if an error occurs while parsing the XPath expression found (will wrap {@link XMLException}.
	 */
	private void addMappingList(String mappingRoot, String outputName, String predefinedNameFormat, String multiValueBehaviour, Integer groupNumber,
					Integer minValueCount, Integer maxValueCount) throws SAXException {
		IMappingContainer parent = (this.mappingListStack.size() > 0) ? this.mappingListStack.peek() : null;
		MappingList container = new MappingList();
		try {
			container.setMappingRoot(XmlUtil.createXPathValue(this.mappingConfiguration.getNamespaceMap(), mappingRoot));
		} catch (XMLException e) {
			throw getException(e, "Invalid XPath \"%s\" found in mapping root for mapping list", mappingRoot);
		}
		container.setParent(parent);
		container.setName(outputName);
		container.setGroupNumber(groupNumber == null ? this.currentGroupNumber : groupNumber);
		container.setMultiValueBehaviour(MultiValueBehaviour.parse(multiValueBehaviour, this.mappingConfiguration.getDefaultMultiValueBehaviour()));
		container.setMinValueCount(minValueCount == null ? 0 : minValueCount);
		container.setMaxValueCount(maxValueCount == null ? 0 : maxValueCount);
		this.mappingListStack.push(container);

		/*
		 * Increment the current group number so that all the children of this container have a default group that doesn't match any other child of
		 * another conatiner.
		 */
		this.currentGroupNumber++;
	}

	/**
	 * Adds a field mapping to the current MappingList instance being defined.
	 *
	 * @param name the name of the field.
	 * @param kvPairRootSource the XPath that, when executed, will return the roots from which <code>keyXPathSource</code> and
	 *            <code>valueXPathSource</code> will be executed.
	 * @param keyXPathSource the XPath that should be executed to each key.
	 * @param valueXPathSource the XPath that should be executed to get the value, relative to each key.
	 * @param templateNameFormatName the name of one of the built-in styles (see {@link NameFormat} public members.
	 * @param groupNumber the group number that applies to this mapping.
	 * @param mappingRootSource an optional XPath expression that returns a set of nodes that will be the basis of the mapping.
	 * @param customTemplateNameFormat a bespoke style to use for this mapping.
	 * @param multiValueBehaviour defines what should happen when multiple values are found for a single evaluation for this mapping.
	 * @throws SAXException if an error occurs while parsing the XPath expression found (will wrap {@link XMLException}.
	 */
	// CHECKSTYLE:OFF Number of parameters is appropriate here, I'm not going to create separate methods or add complexity in to caller.
	private void addPivotMapping(String name, String mappingRootSource, String kvPairRootSource, String keyXPathSource, String valueXPathSource,
					String templateNameFormatName, String customTemplateNameFormat, Integer groupNumber, String multiValueBehaviour)
									throws SAXException {
		// CHECKSTYLE:ON
		MappingList parent = this.mappingListStack.isEmpty() ? null : this.mappingListStack.peek();
		try {
			String mappingName;
			if (StringUtil.isNullOrEmpty(name)) {
				LOG.debug("No name was specified for mapping, so XPath value is used instead {}", keyXPathSource);
				mappingName = keyXPathSource.replace('/', '_');
			} else {
				mappingName = name;
			}

			Map<String, String> namespaceMap = this.mappingConfiguration.getNamespaceMap();
			XPathValue rootXPath = XmlUtil.createXPathValue(namespaceMap, mappingRootSource);
			XPathValue keyXPath = XmlUtil.createXPathValue(namespaceMap, keyXPathSource);
			XPathValue valueXPath = XmlUtil.createXPathValue(namespaceMap, valueXPathSource);
			XPathValue kvPairRoot = XmlUtil.createXPathValue(namespaceMap, kvPairRootSource);
			NameFormat templateNameFormat = NameFormat.parse(templateNameFormatName, customTemplateNameFormat, NameFormat.NO_COUNTS);
			PivotMapping mapping = new PivotMapping();
			mapping.setMappingRoot(rootXPath);
			mapping.setMappingName(mappingName);
			mapping.setKVPairRoot(kvPairRoot);
			mapping.setKeyXPath(keyXPath);
			mapping.setValueXPath(valueXPath);
			mapping.setNameFormat(templateNameFormat);
			mapping.setGroupNumber(groupNumber == null ? this.currentGroupNumber : groupNumber);
			MultiValueBehaviour mvb = MultiValueBehaviour.parse(multiValueBehaviour, MultiValueBehaviour.LAZY);
			mapping.setMultiValueBehaviour(mvb);
			if (parent != null) {
				mapping.setParent(parent);
				parent.add(mapping);
			} else {
				this.mappingConfiguration.addContainer(mapping);
			}
		} catch (XMLException e) {
			throw getException(e, "Unable to add pivot mapping definition to configuration due to a problem in the XML configuration.");
		}

		this.currentGroupNumber++;
	}

	/**
	 * Checks to ensure that the {@link #mappingListStack} is empty.
	 *
	 * @throws SAXException if {@link #mappingListStack} is not empty.
	 */
	@Override
	public void endDocument() throws SAXException {
		if (!this.mappingListStack.empty()) {
			throw getException(null, "Mapping stack should be empty, contains %s elements!", this.mappingListStack.size());
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (LOG.isTraceEnabled()) {
			LOG.trace("endElement(URI={})(localName={})(qName={})", uri, localName, qName);
		}

		if (MAPPING_NAMESPACE.equals(uri)) {
			ElementNames elementName = getElementNameEnum(localName);
			switch (elementName) {
				case FileNameInputFilter:
					endInputFilter();
					break;
				case Filters:
					endFilters();
					break;
				case Mapping:
					break;
				case PivotMapping:
					break;
				case MappingConfiguration:
					break;
				case MappingList:
					endMappingList();
					break;
				case XPathInputFilter:
					endInputFilter();
					break;
				default:
					break;
			}
		}
	}

	/**
	 * Ensures that there are no open filters (would indicate a bug in this code) and wipes out {@link #inputFilterStack}.
	 *
	 * @throws SAXException if the input filter stack isn't empty. Indicates a bug in xml2csv.
	 */
	private void endFilters() throws SAXException {
		if (!this.inputFilterStack.empty()) {
			throw getException(null, "Input filter stack is not empty.  Bug in xml2csv.");
		}
		this.inputFilterStack = null;
	}

	/**
	 * Closes off this input filter definition by popping the top {@link #inputFilterStack} value.
	 */
	private void endInputFilter() {
		this.inputFilterStack.pop();
	}

	/**
	 * Closes off a mapping list, either adding it to the {@link #mappingConfiguration} if a top level mapping, or adding to the parent (top of the
	 * {@link #mappingListStack} stack.
	 */
	private void endMappingList() {
		IMappingContainer current = this.mappingListStack.pop();
		if (this.mappingListStack.size() > 0) {
			this.mappingListStack.peek().add(current);
		} else {
			this.mappingConfiguration.addContainer(current);
		}
	}

	/**
	 * Retrieve an attribute value cast as a Boolean, according the rules specified by XSD spec ({@link DatatypeConverter#parseBoolean(String)}).
	 *
	 * @param atts the set of of attributes to retrieve from.
	 * @param attrName the name of the attribute to retrieve.
	 * @return true or false, depending on the value of the attribute, as per the XSD spec.
	 * @throws SAXException if the value specified by <code>attrName</code> is present but not valid according to the XSD spec for XSD boolean value
	 *             types.
	 */
	private boolean getAttributeValueAsBoolean(Attributes atts, String attrName) throws SAXException {
		String attrValueAsString = atts.getValue(attrName);
		if (attrValueAsString == null) {
			return false;
		} else {
			try {
				boolean parsedBoolean = DatatypeConverter.parseBoolean(attrValueAsString);
				LOG.trace("Parsed {} value {} as {}", attrName, attrValueAsString, parsedBoolean);
				return parsedBoolean;
			} catch (IllegalArgumentException iae) {
				// Thrown by DatatypeConverter.parseBoolean for an invalid argument
				throw getException(iae, "Invalid value \"%s\" found in attriute \"%s\"", attrValueAsString, attrName);
			}
		}
	}

	/**
	 * Retrieve an attribute value cast as an Integer, null if one wasn't specified, or throw an exception if malformed.
	 *
	 * @param atts the set of of attributes to retrieve from.
	 * @param attrName the name of the attribute to retrieve.
	 * @return an integer value of the value in <code>attrName</code>, null if no value was specified.
	 * @throws SAXException thrown if the value found in the attribute is not an integer.
	 */
	private Integer getAttributeValueAsInteger(Attributes atts, String attrName) throws SAXException {
		String attrValueAsString = atts.getValue(attrName);
		if (StringUtil.isNullOrEmpty(attrValueAsString)) {
			LOG.debug("No value specified for {}, returning null", attrName);
			return null;
		}
		try {
			return Integer.parseInt(attrValueAsString);
		} catch (NumberFormatException nfe) {
			throw getException(nfe, "Invalid value for %s found: %s", attrName, attrValueAsString);
		}
	}

	/**
	 * Given an element's local name (namespace URI checking is left to the caller), return an enum value that can be used in switch statements to
	 * branch logic depending on the input.
	 *
	 * @param localName the local name of the element.
	 * @return an enum representation of the element's name
	 * @throws SAXException if the element is not recognised (indicates a bug in xml2csv).
	 */
	private ElementNames getElementNameEnum(String localName) throws SAXException {
		ElementNames elementName;
		try {
			elementName = Enum.valueOf(ElementNames.class, localName);
			return elementName;
		} catch (IllegalArgumentException iae) {
			throw getException(iae, "Unexpected element name found.  This is a bug in xml2csv because the schema validation "
							+ "should have caught this already");
		}
	}

	/**
	 * Creates an exception to be thrown by this content handler, ensuring that formatting is consistent and including locator information.
	 *
	 * @param inner the exception that caused this exception to be created. May be null.
	 * @param message the message formatting string.
	 * @param parameters parameters to the message formatting string.
	 * @return an exception, ready to be thrown.
	 */
	private SAXException getException(Exception inner, String message, Object... parameters) {
		String temp = String.format(message, parameters);
		XMLException de = new XMLException(inner, "Error parsing %s(%d:%d) %s", this.documentLocator.getSystemId(),
						this.documentLocator.getLineNumber(), this.documentLocator.getColumnNumber(), temp);
		SAXException se = new SAXException(de);
		return se;
	}

	/**
	 * Get all the mappings that have been found so far by this parser.
	 *
	 * @return a set of mappings, possibly empty and possible null if no files have been parsed.
	 */
	public MappingConfiguration getMappings() {
		return this.mappingConfiguration;
	}

	/**
	 * Gets a list of the known child names of the parent container. These are used as variable names, available in the XPath evaluation of subsequent
	 * mappings with the same parent.
	 *
	 * @return an array, possibly empty, of known siblings of the current mapping (as determined by the top of {@link #mappingListStack}.
	 */
	private String[] getPreviousSiblingNames() {
		List<String> variables = new ArrayList<String>();
		MappingList currentContainer = this.mappingListStack.peek();
		for (IMapping m : currentContainer) {
			if (m instanceof IValueMapping) {
				IValueMapping vm = (IValueMapping) m;
				variables.add(vm.getName());
			}
		}
		String[] varArray = variables.toArray(new String[0]);
		if (LOG.isDebugEnabled()) {
			LOG.debug("Created variable set for {}: {}", currentContainer.getName(), StringUtil.toStringList(varArray));
		}
		return varArray;
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.documentLocator = locator;
	}

	@Override
	public void startDocument() throws SAXException {
		this.mappingConfiguration = new MappingConfiguration();
	}

	/**
	 * Delegates to various helper methods that manage the opening tag of the following elements: MappingSet, Mapping or Field.
	 *
	 * @param uri the Namespace URI, or the empty string if the element has no Namespace URI or if Namespace processing is not being performed.
	 * @param localName the local name (without prefix), or the empty string if Namespace processing is not being performed.
	 * @param qName the qualified name (with prefix), or the empty string if qualified names are not available.
	 * @param atts the attributes attached to the element. If there are no attributes, it shall be an empty Attributes object. The value of this
	 *            object after startElement returns is undefined.
	 * @throws SAXException if any errors occur, usually caused by bad XPath expressions defined in the mapping input configuration. Typically wraps
	 *             {@link XMLException}
	 */
	@Override
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		if (LOG.isTraceEnabled()) {
			traceElement(uri, localName, qName, atts);
		}

		ElementNames elementName = getElementNameEnum(localName);

		if (MAPPING_NAMESPACE.equals(uri)) {
			switch (elementName) {
				case Mapping:
					addMapping(atts.getValue(NAME_ATTR), atts.getValue(XPATH_ATTR), atts.getValue(NAME_FORMAT_ATTR),
									atts.getValue(CUSTOM_NAME_FORMAT_ATTR), getAttributeValueAsInteger(atts, GROUP_NUMBER_ATTR),
									atts.getValue(MULTI_VALUE_BEHAVIOUR_ATTR), getAttributeValueAsInteger(atts, MIN_VALUES_ATTR),
									getAttributeValueAsInteger(atts, MAX_VALUES_ATTR));
					break;
				case PivotMapping:
					addPivotMapping(atts.getValue(NAME_ATTR), atts.getValue(MAPPING_ROOT_ATTR), atts.getValue(KVPAIR_ROOT_XPATH_ATTR),
									atts.getValue(KEY_XPATH_ATTR), atts.getValue(VALUE_XPATH_ATTR), atts.getValue(NAME_FORMAT_ATTR), null,
									getAttributeValueAsInteger(atts, GROUP_NUMBER_ATTR), atts.getValue(MULTI_VALUE_BEHAVIOUR_ATTR));
					break;
				case MappingList:
					addMappingList(atts.getValue(MAPPING_ROOT_ATTR), atts.getValue(NAME_ATTR), atts.getValue(NAME_FORMAT_ATTR),
									atts.getValue(MULTI_VALUE_BEHAVIOUR_ATTR), getAttributeValueAsInteger(atts, GROUP_NUMBER_ATTR),
									getAttributeValueAsInteger(atts, MIN_VALUES_ATTR), getAttributeValueAsInteger(atts, MAX_VALUES_ATTR));
					break;
				case MappingConfiguration:
					addMappingConfiguration(atts.getValue(NAME_FORMAT_ATTR), atts.getValue(MULTI_VALUE_BEHAVIOUR_ATTR));
					break;
				case Filters:
					startFilters();
					break;
				case XPathInputFilter:
					startXPathFilter(atts.getValue("xPath"));
					break;
				case FileNameInputFilter:
					startFileNameFilter(atts.getValue("fileNameRegex"), getAttributeValueAsBoolean(atts, FILENAME_FILTER_MATCH_LOCAL_ATTR));
					break;
				default:
					LOG.warn("Ignoring element ({}):{} as it isn't supported in this version of xml2csv", uri, localName);
			}
		} else {
			LOG.warn("Ignoring element ({}):{} as it is outside of of the mapping namespace {}", uri, localName, MAPPING_NAMESPACE);
		}
	}

	/**
	 * Adds filters to the mapping configuration.
	 *
	 * @param fileNameRegex a regular expression to match against the filename. May be null.
	 * @param matchLocalFileNameOnly if false then the filter must match the absolute path name, if true then only the file name (no directory
	 *            information) will be used.
	 * @throws SAXException If any errors occur whilst adding the filters.
	 */
	private void startFileNameFilter(String fileNameRegex, boolean matchLocalFileNameOnly) throws SAXException {
		try {
			IInputFilter filter = new FileNameInputFilter(fileNameRegex, matchLocalFileNameOnly);
			addFilter(filter);
		} catch (PatternSyntaxException pse) {
			throw getException(pse, "Invalid Regular Expression {} specified for input filter.", fileNameRegex);
		}
	}

	/**
	 * When a new set of filters are found, ensure that stack is empty.
	 *
	 * @throws SAXException if {@link #inputFilterStack} isn't null.
	 */
	private void startFilters() throws SAXException {
		if (this.inputFilterStack != null) {
			throw getException(null, "New Filter set found, but existing filter set not tidied up.  Bug in xml2csv");
		}
		this.inputFilterStack = new Stack<IInputFilter>();
	}

	/**
	 * Track all namespace declarations that aren't related to the XSD language or the mapping schema. These are required and may be used in the XPath
	 * mappings.
	 *
	 * @param prefix The prefix that will be used within XPath statements in the configuration.
	 * @param uri The URI that this namespace maps on to.
	 * @throws SAXException if a duplicate namespace prefix was found within the configuration file. (Will contain a nested
	 *             {@link FileParserException}.)
	 */
	@Override
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		try {
			LOG.info("startPrefixMapping(Prefix={},Uri={})", prefix, uri);
			String finalPrefix = (StringUtil.isNullOrEmpty(uri)) ? XMLConstants.DEFAULT_NS_PREFIX : prefix;
			this.mappingConfiguration.addNamespaceMapping(finalPrefix, uri);
		} catch (FileParserException fpe) {
			throw getException(fpe, "Duplicate namespace mapping found in configuration");
		}
	}

	/**
	 * Adds filters to the mapping configuration.
	 *
	 * @param xPath an XPath value to match within the document. May be null.
	 * @throws SAXException If any errors occur whilst adding the filters.
	 */
	private void startXPathFilter(String xPath) throws SAXException {
		try {
			IInputFilter filter = new XPathInputFilter(this.mappingConfiguration.getNamespaceMap(), xPath);
			addFilter(filter);
		} catch (XMLException e) {
			throw getException(e, "Unable to parse XPath {} specified for input filter.");
		}
	}

	/**
	 * Outputs trace information about the element being passed.
	 *
	 * @param uri see {@link DefaultHandler#startElement(String, String, String, Attributes)}.
	 * @param localName see {@link DefaultHandler#startElement(String, String, String, Attributes)}.
	 * @param qName see {@link DefaultHandler#startElement(String, String, String, Attributes)}.
	 * @param atts see {@link DefaultHandler#startElement(String, String, String, Attributes)}.
	 */
	private void traceElement(String uri, String localName, String qName, Attributes atts) {
		LOG.trace("startElement(URI={})(localName={})(qName={})", uri, localName, qName);
		for (int i = 0; i < atts.getLength(); i++) {
			LOG.trace("Attr[{}](LocalName={})(QName={})(Type={})(URI={})(Value={})", i, atts.getLocalName(i), atts.getQName(i), atts.getType(i),
							atts.getURI(i), atts.getValue(i));
		}
	}

}