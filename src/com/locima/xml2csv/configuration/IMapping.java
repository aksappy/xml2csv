package com.locima.xml2csv.configuration;

import java.util.List;
import java.util.Stack;

import com.locima.xml2csv.util.Tuple;

/**
 * The basic interface for any kind of mapping (may map single or multiple data items).
 */
public interface IMapping {

	/**
	 * The group number of this mapping. Each mapping in the same group is incremented at the same time.
	 *
	 * @return the group number.
	 */
	int getGroupNumber();

	/**
	 * Retrieves the multi-value behaviour (inline or multi-record) for this mapping.
	 *
	 * @return
	 */
	MultiValueBehaviour getMultiValueBehaviour();

	/**
	 * Defines how multiple values being found by this mapping, for a single input, should be named.
	 *
	 * @return an inline format specification.
	 */
	NameFormat getNameFormat();

	/**
	 * Retrieves the parent of this mapping.
	 *
	 * @return either a valid {@link IMappingContainer} instance, or null if this is the top-level container (i.e. it is contained by the single
	 *         {@link MappingConfiguration}.
	 */
	IMappingContainer getParent();

	/**
	 * Determines whether a mapping will always output the same number of values, or whether it's variable.
	 * <p>
	 * Only unbounded inline and pivot mappings can cause variable numbers of values.
	 *
	 * @return true if the mapping always outputs a fixed number of values,false otherwise.
	 */
	boolean hasFixedOutputCardinality();

	/**
	 * Get the most number of results that this mapping can return. Any found after this number are discarded. Values of 0 means no maximum limit.
	 * 
	 * @return the most number of results that this mapping can return. Any found after this number are discarded. Values of 0 means no maximum limit.
	 */
	int getMaxValueCount();

	/**
	 * Get the minimum number of results that this mapping can return. If there are not enough values found to make up this number, then nulls are
	 * added.
	 * 
	 * @return the most number of results that this mapping can return. If there are not enough values found to make up this number, then nulls are
	 *         added.
	 */
	int getMinValueCount();

	/**
	 * Get the most number of results that this mapping has found so far in a single evaluation.
	 * <p>
	 * Whilst this isn't static configuration (it's set during the execution of mappings), I needed something to keep track of during execution of
	 * mappings. The alternative was to create another shared object between ExtractionContext instances have a common mapping. This would probably
	 * involve creating a shared factory that needs to be progated throughout all ExtractionContext instances. Therefore it makes sense to put it here
	 * for now.
	 */
	int getHighestFoundValueCount();
	
	/**
	 * Combines the values in {@link #getMinValueCount()} and {@link #getHighestFoundValueCount()} to determine the total number of 
	 * fields that this mapping should output <em>in a single record</em>.
	 * @return a natural number greater than or equal to one.
	 */
	int getFieldCountForSingleRecord();	

	/**
	 * Sets the most number of results found with this mapping during a single evaluation.
	 * @param valueFound the number of results found in a single evaluation.  Will be ignored if a previous call has found a higher value.
	 * @see IMapping#getHighestFoundValueCount()
	 */
	void setHighestFoundValueCount(int valueFound);
	
}
