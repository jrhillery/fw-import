/*
 * Created on Feb 5, 2019
 */
package com.leastlogic.mdimport.util;

import java.awt.Component;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.leastlogic.moneydance.util.MdUtil;

public class CsvChooser {
	private Component parent;
	private Locale locale;
	private Path defaultDirectory;

	private ResourceBundle msgBundle = null;

	private static final String baseMessageBundleName = "com.leastlogic.mdimport.util.MdUtilMessages";
	private static final String CSV_EXT = "csv";

	/**
	 * @param parent root pane
	 */
	public CsvChooser(Component parent) {
		this.parent = parent;
		this.locale = parent.getLocale();
		this.defaultDirectory = Paths.get(System.getProperty("user.home"), "Downloads");

	} // end (Component) constructor

	/**
	 * @param defaultFileGlobPattern
	 * @return the selected file, if any
	 */
	public Path chooseCsvFile(String defaultFileGlobPattern) {
		JFileChooser chooser = new JFileChooser(this.defaultDirectory.toFile());
		chooser.setDialogTitle(getTitle());
		chooser.setApproveButtonToolTipText(
			getMsgBundle().getString("CsvChooser.approve.toolTipText"));
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter(
			getMsgBundle().getString("CsvChooser.csv.text"), CSV_EXT));
		Path defaultFile = getDefaultFile(defaultFileGlobPattern);

		if (defaultFile != null) {
			chooser.setSelectedFile(defaultFile.toFile());
		}
		int result = chooser.showDialog(this.parent,
			getMsgBundle().getString("CsvChooser.approve.text"));

		return result == JFileChooser.APPROVE_OPTION
			? chooser.getSelectedFile().toPath()
			: null;
	} // end chooseCsvFile(String)

	/**
	 * @param defaultFileGlobPattern
	 * @return the default file, if a unique one exists matching the supplied glob pattern
	 */
	public Path getDefaultFile(String defaultFileGlobPattern) {
		Path foundOne = null;
		int numFound = 0;

		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(this.defaultDirectory,
				defaultFileGlobPattern + '.' + CSV_EXT)) {

			for (Path path : dirStream) {
				foundOne = path;
				++numFound;
			}
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}

		return numFound == 1 ? foundOne : null;
	} // end getDefaultFile(String)

	/**
	 * @return Our title
	 */
	public String getTitle() {

		return getMsgBundle().getString("CsvChooser.title");
	} // end getTitle()

	/**
	 * @return Our message bundle
	 */
	private ResourceBundle getMsgBundle() {
		if (this.msgBundle == null) {
			this.msgBundle = MdUtil.getMsgBundle(baseMessageBundleName, this.locale);
		}

		return this.msgBundle;
	} // end getMsgBundle()

} // end class CsvChooser
