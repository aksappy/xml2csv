package com.locima.xml2csv.extractor;

import net.sf.saxon.s9api.XdmNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.locima.xml2csv.ArgumentNullException;
import com.locima.xml2csv.BugException;
import com.locima.xml2csv.configuration.IMapping;
import com.locima.xml2csv.configuration.IMappingContainer;
import com.locima.xml2csv.configuration.IValueMapping;

/**
 * An extraction context provides an object that understands how to evaluate individual mappings or containers of multiple mappings recursively, and
 * store the results of that evaluation such that output records can be generated.
 * <p>
 * There is a 1..n relationship between an {@link IMapping} instance and an {@link ExtractionContext}. Each time an XML root node is found from which
 * the {@link IMapping} instance requires evaulation, a corresponding {@link ExtractionContext} is created to manage the execution and store the
 * results. Therefore for each execution of an {@link IMapping} that this context manages, either:
 * <ol>
 * <li>A new ordered list of values is created, in the case that this context is managing an {@link IValueMapping} (see
 * {@link MappingExtractionContext}), or</li>
 * <li>An ordered list of references to child contexts are created for {@link IMappingContainer} (see {@link ContainerExtractionContext}), each
 * reference containing an index.</li>
 * </ol>
 * How these tree-structured sets of values are then flattened in to a CSV file is performed in {@link OutputRecordIterator} and dependent on the
 * configuration of the {@link IMapping} instance, specifically the {@link IMapping#getMultiValueBehaviour()} value.
 */
public abstract class ExtractionContext {

	private static final Logger LOG = LoggerFactory.getLogger(ExtractionContext.class);

	/**
	 * Factory method to create the right type of {@link ExtractionContext} (either {@link MappingExtractionContext} or
	 * {@link ContainerExtractionContext}) based on the sub-type of the <code>mapping</code> parameter.
	 * <p>
	 * I've done it this way so an {@link ExtractionContext} instance can be easily created using an {@link IMapping} instance (i.e. the type checking
	 * is done here).
	 * 
	 * @param parent the parent context (in the same way that an {@link IMapping} has a parent).
	 * @param mapping the mapping that the new context will be managing.
	 * @param index the index of the new context, with respect to its siblings (first child of the parent has index 0, second has index 1, etc.).
	 * @return either a {@link MappingExtractionContext} or {@link ContainerExtractionContext} instance. Never null.
	 */
	public static ExtractionContext create(ContainerExtractionContext parent, IMapping mapping, int index) {
		ExtractionContext ctx;
		if (mapping == null) {
			throw new ArgumentNullException("mapping");
		}
		if (mapping instanceof IValueMapping) {
			ctx = new MappingExtractionContext(parent, ((IValueMapping) mapping));
		} else if (mapping instanceof IMappingContainer) {
			ctx = new ContainerExtractionContext(parent, (IMappingContainer) mapping, index);
		} else {
			throw new BugException("Passed mapping that is not a value mapping or mapping container: %s", mapping);
		}
		return ctx;
	}

	private int currentIndex;

	/**
	 * The parent of this extraction context instance. If this is managing the evaluation of a top level {@link IMappingContainer} then this will be
	 * <code>null</code>.
	 */
	private ContainerExtractionContext parent;

	protected ExtractionContext(ContainerExtractionContext parent) {
		this.parent = parent;
	}

	public abstract void clearResults();

	public abstract void evaluate(XdmNode rootNode) throws DataExtractorException;

	public abstract IMapping getMapping();

	public abstract String getName();

	public ContainerExtractionContext getParent() {
		return this.parent;
	}

	public void incrementContext() {
		this.currentIndex++;
		LOG.debug("Incrementing context of {} to {}", this, this.currentIndex);
	}

	public abstract void resetContext();

	public void resetForNewDoc() {
		resetContext();
		clearResults();
	}

	protected void setIndex(int newIndex) {
		this.currentIndex = newIndex;
	}

	public abstract int size();
}
