package com.locima.xml2csv.inputparser;

/**
 * <p>
 * The fields in a formatting string are:
 * <table>
 * <thead>
 * <tr>
 * <th>Parameter index</th>
 * <th>Meaning</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <td>1</td>
 * <td>The current mapping name for the mapping.</td>
 * </tr>
 * <tr>
 * <td>2</td>
 * <td>The current mapping iteration number within the parent.</td>
 * </tr>
 * <tr>
 * <td>3</td>
 * <td>The current mapping name of the parent.</td>
 * </tr>
 * <tr>
 * <td>4</td>
 * <td>The current mapping's parent iteration number within it's own parent.</td>
 * </tr>
 * <tr>
 * <td>5</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td></td>
 * <td></td>
 * </tr>
 * </tbody>
 * </table>
 */
public class InlineFormat {

	public static final InlineFormat NO_COUNTS = new InlineFormat("%1$s");

	public static final InlineFormat WITH_COUNT = new InlineFormat("%1$s_%2$d");

	public static final InlineFormat WITH_COUNT_AND_PARENT_COUNT = new InlineFormat("%1$s_%4$d_%2$d");
	public static final InlineFormat WITH_PARENT_COUNT = new InlineFormat("%1$s_%4$d");
	private String format;

	public InlineFormat(String format) {
		this.format = format;
	}

	public String format(String columnName, int iterationNumber, String parentName, int parentIterationNumber) {
		return String.format(this.format, columnName, iterationNumber + 1, parentName, parentIterationNumber + 1);
	}
}
