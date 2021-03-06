package com.locima.xml2csv.output.direct;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.locima.xml2csv.configuration.IMappingContainer;
import com.locima.xml2csv.output.IExtractionResultsContainer;
import com.locima.xml2csv.output.IOutputManager;
import com.locima.xml2csv.output.IOutputWriter;
import com.locima.xml2csv.output.OutputManagerException;
import com.locima.xml2csv.output.OutputUtil;
import com.locima.xml2csv.output.inline.InlineCsvWriter;
import com.locima.xml2csv.util.FileUtility;
import com.locima.xml2csv.util.StringUtil;

/**
 * Manages the output of a single CSV file where the results of conversion from XML when the mapping configuration prohibits a variable number of
 * fields in a record.
 * <p>
 * We want to use this {@link IOutputManager} whenever possible as this is much faster to work because it writes CSV files directly. If the number of
 * fields in a CSV depends on the result of mappings (i.e. executing an XPath statement yields n results in an inline mapping) then we have to use a
 * {@link InlineCsvWriter} instead.
 */
public class DirectCsvWriter implements IOutputWriter {

	private static final Logger LOG = LoggerFactory.getLogger(DirectCsvWriter.class);

	private File outputFile;

	private String outputName;

	private Writer writer;

	/**
	 * In the case of a direct CSV writer, all we can do is attempt to close the file.
	 */
	@Override
	public void abort() {
		close();
	}

	@Override
	public void close() {
		LOG.info("Closing writer for {} writer in {}", this.outputName, this.outputFile.getAbsolutePath());
		try {
			this.writer.close();
		} catch (IOException ioe) {
			// No point throwing the error up as there's no useful action to be taken at this point
			LOG.error("Error closing writer", ioe);
		}
	}

	/**
	 * Initialise the CSV file that this writer is repsonsible for.
	 *
	 * @param outputDirectory the directory to write the output CSV file to.
	 * @param container the mapping container that determines what outputs will be written. Must not be null.
	 * @param appendOutput true if output should be appended to existing files, false if new files should overwrite existing ones.
	 * @throws OutputManagerException if an unrecoverable error occurs whilst creating the output files or writing to them.
	 */
	@Override
	public void initialise(File outputDirectory, IMappingContainer container, boolean appendOutput) throws OutputManagerException {
		this.outputName = container.getName();
		String fileNameBasis = this.outputName;
		this.outputFile = new File(outputDirectory, FileUtility.convertToPOSIXCompliantFileName(fileNameBasis, ".csv", true));
		this.writer = OutputUtil.createCsvWriter(container, this.outputFile, appendOutput);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("DirectCsvWriter(");
		sb.append(this.outputName);
		sb.append(", ");
		sb.append(this.outputFile);
		sb.append(")");
		return sb.toString();
	}

	/**
	 * Writes a set of values out to the specified writer using CSV notation.
	 *
	 * @param resultsContainer the results to write to the CSV file.
	 * @throws OutputManagerException if an error occurred writing the files.
	 */
	@Override
	public void writeRecords(IExtractionResultsContainer resultsContainer) throws OutputManagerException {
		DirectOutputRecordIterator iter = new DirectOutputRecordIterator(resultsContainer);
		while (iter.hasNext()) {
			List<String> record = iter.next();
			String outputLine = StringUtil.toCsvRecord(record);
			try {
				if (LOG.isTraceEnabled()) {
					LOG.trace("Writing output {}: {}", this.outputFile.getAbsolutePath(), outputLine);
				}
				this.writer.write(outputLine);
				this.writer.write(StringUtil.LINE_SEPARATOR);
			} catch (IOException ioe) {
				throw new OutputManagerException(ioe, "Unable to write to %1$s(%2$s): %3$s", this.outputName, this.outputFile.getAbsolutePath(),
								outputLine);
			}
		}
	}

}
