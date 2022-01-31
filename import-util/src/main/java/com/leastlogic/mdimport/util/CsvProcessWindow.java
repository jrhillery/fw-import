/*
 * Created on Jan 21, 2018
 */
package com.leastlogic.mdimport.util;

import java.nio.file.Path;
import java.util.Locale;

public interface CsvProcessWindow {

	/**
	 * @return The file selected to import
	 */
	Path getFileToImport();

	/**
	 * @param text HTML text to append to the output log text area
	 */
	void addText(String text);

	/**
	 * @return The Locale object that is associated with this window
	 */
	Locale getLocale();

} // end interface CsvProcessWindow
