/*
 * Created on Dec 14, 2017
 */
package com.moneydance.modules.features.fwimport;

import com.leastlogic.mdimport.util.CsvChooser;
import com.leastlogic.mdimport.util.CsvProcessWindow;
import com.leastlogic.moneydance.util.MdLog;
import com.leastlogic.moneydance.util.MdStorageUtil;
import com.leastlogic.moneydance.util.StagedInterface;
import com.leastlogic.swing.util.AwtScreenUtil;
import com.leastlogic.swing.util.HTMLPane;

import javax.swing.*;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultFormatter;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.Serial;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.ResourceBundle;

import static javax.swing.GroupLayout.DEFAULT_SIZE;
import static javax.swing.GroupLayout.PREFERRED_SIZE;

public class FwImportWindow extends JFrame implements CsvProcessWindow {
	private final Main feature;
	private final MdStorageUtil mdStorage;
	private final CsvChooser chooser;
	private JFormattedTextField txtFileToImport;
	private JButton btnChooseFile;
	private JButton btnImport;
	private JButton btnCommit;
	private HTMLPane pnOutputLog;
	private final AwtScreenUtil screenUtil = new AwtScreenUtil(this);
	private StagedInterface staged = null;
	private final ArrayDeque<AutoCloseable> closeableResources = new ArrayDeque<>();

	static final String baseMessageBundleName = "com.moneydance.modules.features.fwimport.FwImportMessages"; //$NON-NLS-1$
	private static final ResourceBundle msgBundle = ResourceBundle.getBundle(baseMessageBundleName);
	private static final String DEFAULT_FILE_GLOB_PATTERN = "NbPosition*";
	@Serial
	private static final long serialVersionUID = -2854101228415634711L;

	/**
	 * Create the frame.
	 *
	 * @param feature Our main feature module
	 * @param storage Moneydance local storage
	 */
	public FwImportWindow(Main feature, Map<String, String> storage) {
		super(msgBundle.getString("FwImportWindow.window.title")); //$NON-NLS-1$
		this.feature = feature;
		this.mdStorage = new MdStorageUtil("fw-import", storage);
		this.chooser = new CsvChooser(getRootPane());
		initComponents();
		wireEvents();
		readIconImage();

	} // end constructor

	/**
	 * Initialize swing components.
	 */
	private void initComponents() {
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		this.screenUtil.setWindowCoordinates(this.mdStorage, 577, 357);
		JPanel contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);

		JLabel lblFileToImport = new JLabel(msgBundle.getString("FwImportWindow.lblFileToImport.text")); //$NON-NLS-1$

		Path defaultFile = this.chooser.getDefaultFile(DEFAULT_FILE_GLOB_PATTERN);
		DefaultFormatter formatter = new DefaultFormatter();
		formatter.setOverwriteMode(false);
		this.txtFileToImport = new JFormattedTextField(formatter);
		this.txtFileToImport.setFocusLostBehavior(JFormattedTextField.PERSIST);
		this.txtFileToImport.setToolTipText(msgBundle.getString("FwImportWindow.txtFileToImport.toolTipText")); //$NON-NLS-1$

		if (defaultFile != null)
			this.txtFileToImport.setValue(defaultFile.toString());
		else
			this.txtFileToImport.setText('[' + this.chooser.getTitle() + ']');

		this.btnChooseFile = new JButton(msgBundle.getString("FwImportWindow.btnChooseFile.text")); //$NON-NLS-1$
		reducePreferredHeight(this.btnChooseFile);
		this.btnChooseFile.setToolTipText(msgBundle.getString("FwImportWindow.btnChooseFile.toolTipText")); //$NON-NLS-1$

		this.btnImport = new JButton(msgBundle.getString("FwImportWindow.btnImport.text")); //$NON-NLS-1$
		this.btnImport.setEnabled(defaultFile != null);
		reducePreferredHeight(this.btnImport);
		this.btnImport.setToolTipText(msgBundle.getString("FwImportWindow.btnImport.toolTipText")); //$NON-NLS-1$

		this.btnCommit = new JButton(msgBundle.getString("FwImportWindow.btnCommit.text")); //$NON-NLS-1$
		this.btnCommit.setEnabled(false);
		reducePreferredHeight(this.btnCommit);
		this.btnCommit.setToolTipText(msgBundle.getString("FwImportWindow.btnCommit.toolTipText")); //$NON-NLS-1$

		this.pnOutputLog = new HTMLPane();
		JScrollPane scrollPane = new JScrollPane(this.pnOutputLog);
		GroupLayout gl_contentPane = new GroupLayout(contentPane);
		gl_contentPane.setHorizontalGroup(
			gl_contentPane.createParallelGroup(Alignment.TRAILING)
				.addGroup(gl_contentPane.createSequentialGroup()
					.addComponent(lblFileToImport)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(this.txtFileToImport, DEFAULT_SIZE, 383, Short.MAX_VALUE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(this.btnChooseFile))
				.addGroup(gl_contentPane.createSequentialGroup()
					.addContainerGap()
					.addComponent(this.btnImport)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(this.btnCommit))
				.addComponent(scrollPane, DEFAULT_SIZE, 548, Short.MAX_VALUE)
		);
		gl_contentPane.setVerticalGroup(
			gl_contentPane.createParallelGroup(Alignment.LEADING)
				.addGroup(gl_contentPane.createSequentialGroup()
					.addGroup(gl_contentPane.createParallelGroup(Alignment.BASELINE)
						.addComponent(lblFileToImport)
						.addComponent(this.txtFileToImport, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
						.addComponent(this.btnChooseFile))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addGroup(gl_contentPane.createParallelGroup(Alignment.BASELINE)
						.addComponent(this.btnImport)
						.addComponent(this.btnCommit))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(scrollPane, DEFAULT_SIZE, 235, Short.MAX_VALUE))
		);
		gl_contentPane.linkSize(SwingConstants.HORIZONTAL, this.btnChooseFile, this.btnImport, this.btnCommit);
		contentPane.setLayout(gl_contentPane);

	} // end initComponents()

	/**
	 * @param button The button to adjust
	 */
	private void reducePreferredHeight(JComponent button) {
		Dimension textDim = this.txtFileToImport.getPreferredSize();
		HTMLPane.reduceHeight(button, textDim.height);

	} // end reducePreferredHeight(JComponent)

	/**
	 * Wire in our event listeners.
	 */
	private void wireEvents() {
		this.txtFileToImport.addPropertyChangeListener("value", event ->
			this.btnImport.setEnabled(true));
		this.btnChooseFile.addActionListener(event ->
			setFileToImport(this.chooser.chooseCsvFile(DEFAULT_FILE_GLOB_PATTERN)));
		this.btnImport.addActionListener(event -> {
			if (this.feature != null) {
				this.feature.importFile();
			}
		});
		this.btnCommit.addActionListener(event -> {
			// invoked when Commit is selected
			if (this.staged != null) {
				try {
					this.staged.commitChanges().ifPresent(summary -> {
						MdLog.all(summary);
						this.pnOutputLog.addText(summary);
					});
					enableCommitButton(this.staged.isModified());
				} catch (Exception e) {
					MdLog.all("Problem committing changes", e);
					addText(e.toString());
					enableCommitButton(false);
				}
			}
		}); // end btnCommit.addActionListener

	} // end wireEvents()

	/**
	 * Read in and set our icon image.
	 */
	private void readIconImage() {
		HTMLPane.readResourceImage("flat-funnel-32.png", getClass()) //$NON-NLS-1$
				.ifPresent(this::setIconImage);

	} // end readIconImage()

	/**
	 * @return the file selected to import
	 */
	public Path getFileToImport() {
		String fileToImport = (String) this.txtFileToImport.getValue();

		return fileToImport == null ? Paths.get("") : Paths.get(fileToImport); //$NON-NLS-1$
	} // end getFileToImport()

	/**
	 * @param file The location of the file selected to import
	 */
	private void setFileToImport(Path file) {
		if (file != null) {
			this.txtFileToImport.setValue(file.toString());
		}

	} // end setFileToImport(Path)

	/**
	 * @param text HTML-text to append to the output log text area
	 */
	public void addText(String text) {
		MdLog.debug(text);
		this.pnOutputLog.addText(text);

	} // end addText(String)

	/**
	 * Clear the output log text area.
	 */
	public void clearText() {
		this.pnOutputLog.clearText();

	} // end clearText()

	/**
	 * @param b true to enable the button, otherwise false
	 */
	public void enableCommitButton(boolean b) {
		this.btnCommit.setEnabled(b);

	} // end enableCommitButton(boolean)

	/**
	 * Store the object to manage staged changes.
	 *
	 * @param staged The object managing staged changes
	 */
	public void setStaged(StagedInterface staged) {
		this.staged = staged;

	} // end setStaged(StagedInterface)

	/**
	 * Store an object with resources to close.
	 *
	 * @param closeable The object managing closeable resources
	 */
	public void addCloseableResource(AutoCloseable closeable) {
		this.closeableResources.addFirst(closeable);

	} // end addCloseableResource(AutoCloseable)

	/**
	 * Processes events on this window.
	 *
	 * @param event The event to be processed
	 */
	protected void processEvent(AWTEvent event) {
		if (event.getID() == WindowEvent.WINDOW_CLOSING) {
			goAway();
		} else {
			super.processEvent(event);
		}

	} // end processEvent(AWTEvent)

	/**
	 * Remove this frame.
	 *
	 * @return null
	 */
	public FwImportWindow goAway() {
		this.screenUtil.persistWindowCoordinates(this.mdStorage);
		setVisible(false);
		dispose();

		while (!this.closeableResources.isEmpty()) {
			// Release any resources we acquired.
			try {
				this.closeableResources.removeFirst().close();
			} catch (Exception e) {
				MdLog.all("Problem closing resource", e);
			}
		}

		return null;
	} // end goAway()

} // end class FwImportWindow
