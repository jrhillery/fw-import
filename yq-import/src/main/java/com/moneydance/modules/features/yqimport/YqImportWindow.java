/*
 * Created on Jan 17, 2018
 */
package com.moneydance.modules.features.yqimport;

import static javax.swing.GroupLayout.DEFAULT_SIZE;
import static javax.swing.GroupLayout.PREFERRED_SIZE;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultFormatter;

import com.leastlogic.moneydance.util.CsvProcessWindow;
import com.leastlogic.swing.util.HTMLPane;

public class YqImportWindow extends JFrame implements ActionListener, CsvProcessWindow {
	private Main feature;
	private JFormattedTextField txtFileToImport;
	private JButton btnChooseFile;
	private JButton btnImport;
	private JButton btnCommit;
	private HTMLPane pnOutputLog;

	private static final String CHOOSER_TITLE = "Select file to import";
	private static final long serialVersionUID = -1116157696854186533L;

	/**
	 * Create the frame.
	 *
	 * @param feature
	 */
	public YqImportWindow(Main feature) {
		super("Yahoo quote import");
		this.feature = feature;
		initComponents();
		wireEvents();
		readIconImage();

	} // end (Main) constructor

	/**
	 * Initialize the swing components.
	 */
	private void initComponents() {
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		setSize(576, 356);
		JPanel contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);

		JLabel lblFileToImport = new JLabel("File to import");

		DefaultFormatter formatter = new DefaultFormatter();
		formatter.setOverwriteMode(false);
		this.txtFileToImport = new JFormattedTextField(formatter);
		this.txtFileToImport.setToolTipText("This file will be imported");
		this.txtFileToImport.setText('[' + CHOOSER_TITLE + ']');

		this.btnChooseFile = new JButton("Choose");
		reducePreferredHeight(this.btnChooseFile);
		this.btnChooseFile.setToolTipText("Use file picker to choose");

		this.btnImport = new JButton("Import");
		reducePreferredHeight(this.btnImport);
		this.btnImport.setToolTipText("Import data from the specified file");

		this.btnCommit = new JButton("Commit");
		this.btnCommit.setEnabled(false);
		reducePreferredHeight(this.btnCommit);
		this.btnCommit.setToolTipText("Commit changes to Moneydance");

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
		gl_contentPane.linkSize(SwingConstants.HORIZONTAL, new Component[] {this.btnChooseFile, this.btnImport, this.btnCommit});
		contentPane.setLayout(gl_contentPane);

	} // end initComponents()

	/**
	 * @param button
	 */
	private void reducePreferredHeight(JComponent button) {
		Dimension textDim = this.txtFileToImport.getPreferredSize();
		HTMLPane.reduceHeight(button, textDim.height);

	} // end reducePreferredHeight(JComponent)

	/**
	 * Wire in our event listeners.
	 */
	private void wireEvents() {
		this.btnChooseFile.addActionListener(this);
		this.btnImport.addActionListener(this);
		this.btnCommit.addActionListener(this);

	} // end wireEvents()

	/**
	 * Read in and set our icon image.
	 */
	private void readIconImage() {
		setIconImage(HTMLPane.readResourceImage("flat-funnel-32.png", this));

	} // end readIconImage()

	/**
	 * Invoked when an action occurs.
	 *
	 * @param event
	 */
	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();

		if (source == this.btnChooseFile) {
			JFileChooser chooser = new JFileChooser(
					new File(System.getenv("HOMEPATH"), "Downloads"));
			chooser.setDialogTitle(CHOOSER_TITLE);
			chooser.setApproveButtonToolTipText("Use the selected file");
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setFileFilter(new FileNameExtensionFilter("Comma separated value", "csv"));
			int result = chooser.showDialog(getRootPane(), "Select");

			if (result == JFileChooser.APPROVE_OPTION) {
				setFileToImport(chooser.getSelectedFile());
			}
		}

		if (source == this.btnImport && this.feature != null) {
			this.feature.importFile();
		}

		if (source == this.btnCommit && this.feature != null) {
			this.feature.commitChanges();
		}

	} // end actionPerformed(ActionEvent)

	/**
	 * @return the file selected to import
	 */
	public File getFileToImport() {
		String fileToImport = (String) this.txtFileToImport.getValue();

		return fileToImport == null ? null : new File(fileToImport);
	} // end getFileToImport()

	/**
	 * @param file
	 */
	private void setFileToImport(File file) {
		this.txtFileToImport.setValue(file.getPath());

	} // end setFileToImport(File)

	/**
	 * @param text HTML text to append to the output log text area
	 */
	public void addText(String text) {
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
	 * Processes events on this window.
	 *
	 * @param event
	 */
	protected void processEvent(AWTEvent event) {
		if (event.getID() == WindowEvent.WINDOW_CLOSING) {
			if (this.feature != null) {
				this.feature.closeWindow();
			} else {
				goAway();
			}
		} else {
			super.processEvent(event);
		}

	} // end processEvent(AWTEvent)

	/**
	 * Remove this frame.
	 *
	 * @return null
	 */
	public YqImportWindow goAway() {
		setVisible(false);
		dispose();

		return null;
	} // end goAway()

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					YqImportWindow frame = new YqImportWindow(null);
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

	} // end main(String[])

} // end class YqImportWindow
