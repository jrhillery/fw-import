/*
 * Created on Feb 5, 2019
 */
package com.leastlogic.mdimport.util;

import java.awt.Component;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.leastlogic.moneydance.util.MdLog;

public class CsvChooser {
	private final Component parent;
	private final Path defaultDirectory;

	private static final String baseMessageBundleName = "com.leastlogic.mdimport.util.MdUtilMessages";
	private static final ResourceBundle msgBundle = ResourceBundle.getBundle(baseMessageBundleName);
	private static final String CSV_EXT = "csv";

	/**
	 * @param parent root pane
	 */
	public CsvChooser(Component parent) {
		this.parent = parent;
		this.defaultDirectory = Paths.get(System.getProperty("user.home"), "Downloads");

	} // end (Component) constructor

	/**
	 * @param defaultFileGlobPattern The file name pattern for our default
	 * @return the selected file, if any
	 */
	public Path chooseCsvFile(String defaultFileGlobPattern) {
		JFileChooser chooser = new JFileChooser(this.defaultDirectory.toFile());
		chooser.setDialogTitle(getTitle());
		chooser.setApproveButtonToolTipText(msgBundle.getString("CsvChooser.approve.toolTipText"));
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter(
			msgBundle.getString("CsvChooser.csv.text"), CSV_EXT));
		Path defaultFile = getDefaultFile(defaultFileGlobPattern);

		if (defaultFile != null) {
			chooser.setSelectedFile(defaultFile.toFile());
		}
		int result = chooser.showDialog(this.parent, msgBundle.getString("CsvChooser.approve.text"));

		return result == JFileChooser.APPROVE_OPTION
			? chooser.getSelectedFile().toPath()
			: null;
	} // end chooseCsvFile(String)

	/**
	 * @param defaultFileGlobPattern The file name pattern for our default
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
			MdLog.all("Problem finding files like %s to import from %s"
					.formatted(defaultFileGlobPattern, this.defaultDirectory), e);
		}

		return numFound == 1 ? foundOne : null;
	} // end getDefaultFile(String)

	/**
	 * @return Our title
	 */
	public String getTitle() {

		return msgBundle.getString("CsvChooser.title");
	} // end getTitle()

} // end class CsvChooser
