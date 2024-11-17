/*
 * Created on Jan 21, 2018
 */
package com.leastlogic.mdimport.util;

import com.leastlogic.moneydance.util.MdUtil;
import com.leastlogic.moneydance.util.MduException;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public abstract class CsvProcessor implements AutoCloseable {
	protected final CsvProcessWindow impWin;
	protected final Locale locale;
	private final String propertiesFileName;

	private final Map<String, String> csvRowMap = new LinkedHashMap<>();
	private Properties csvProps = null;

	private static final char DOUBLE_QUOTE = '"';

	/**
	 * Sole constructor.
	 *
	 * @param importWindow       Our import console
	 * @param propertiesFileName Our properties file name
	 */
	protected CsvProcessor(CsvProcessWindow importWindow, String propertiesFileName) {
		this.impWin = importWindow;
		this.locale = importWindow.getLocale();
		this.propertiesFileName = propertiesFileName;
		importWindow.addCloseableResource(this);

	} // end (CsvProcessWindow, Locale, String, String) constructor

	/**
	 * Import this row of the comma separated value file.
	 */
	abstract protected void processRow() throws MduException;

	/**
	 * Process each row in the selected comma separated value file.
	 */
	protected void processFile() throws MduException {
		BufferedReader reader = openFile();
		if (reader == null)
			return; // nothing to import

		try {
			String[] header = readLine(reader);

			while (hasMore(reader)) {
				String[] values = readLine(reader);

				if (header != null && values != null) {
					this.csvRowMap.clear();

					for (int i = 0; i < header.length; ++i) {
						if (i < values.length) {
							this.csvRowMap.put(header[i], values[i]);
						} else {
							this.csvRowMap.put(header[i], "");
						}
					} // end for

					processRow();
				}
			} // end while
		} finally {
			close(reader);
		}

	} // end processFile()

	/**
	 * @param propKey Property key for column header
	 * @return Value from the csv row map with any surrounding double quotes removed
	 */
	protected String getCol(String propKey) throws MduException {
		String csvColumnKey = getCsvProps().getProperty(propKey);
		String val = this.csvRowMap.get(csvColumnKey);
		if (val == null) {
			throw new MduException(null, "Unable to locate column %s (%s) in %s; Found columns %s",
				csvColumnKey, propKey, this.impWin.getFileToImport(), this.csvRowMap.keySet());
		}
		int quoteLoc = val.indexOf(DOUBLE_QUOTE);

		if (quoteLoc == 0) {
			// starts with a double quote
			quoteLoc = val.lastIndexOf(DOUBLE_QUOTE);

			if (quoteLoc == val.length() - 1) {
				// also ends with a double quote => remove them
				val = val.substring(1, quoteLoc);
			}
		}

		return val.trim();
	} // end getCol(String)

	/**
	 * @return A buffered reader to read from the file selected to import
	 */
	private BufferedReader openFile() {
		BufferedReader reader = null;
		try {
			reader = Files.newBufferedReader(this.impWin.getFileToImport());
		} catch (Exception e) {
			this.impWin.addText("Exception opening file %s: %s"
				.formatted(this.impWin.getFileToImport(), e));
		}

		return reader;
	} // end openFile()

	/**
	 * @param reader The buffered reader for the file we are importing
	 * @return True when the next read will not block for input, false otherwise
	 */
	private boolean hasMore(BufferedReader reader) throws MduException {
		try {

			return reader.ready();
		} catch (Exception e) {
			throw new MduException(e, "Exception checking file %s", this.impWin.getFileToImport());
		}
	} // end hasMore(BufferedReader)

	/**
	 * @param reader The buffered reader for the file we are importing
	 * @return The comma separated tokens from the next line in the file
	 */
	private String[] readLine(BufferedReader reader) throws MduException {
		try {
			String line = reader.readLine();

			return line == null ? null : line.split(",");
		} catch (Exception e) {
			throw new MduException(e, "Exception reading from file %s",
				this.impWin.getFileToImport());
		}
	} // end readLine(BufferedReader)

	/**
	 * Close the specified reader, ignoring any exceptions.
	 *
	 * @param reader The buffered reader for the file we are importing
	 */
	private static void close(BufferedReader reader) {
		try {
			reader.close();
		} catch (Exception e) { /* ignore */ }

	} // end close(BufferedReader)

	/**
	 * @return Our properties
	 */
	private Properties getCsvProps() throws MduException {
		if (this.csvProps == null) {
			this.csvProps = MdUtil.loadProps(this.propertiesFileName, getClass());
		}

		return this.csvProps;
	} // end getCsvProps()

	/**
	 * Close this resource, relinquishing any underlying resources.
	 */
	public void close() {
		// nothing to release

	} // end close()

} // end class CsvProcessor
