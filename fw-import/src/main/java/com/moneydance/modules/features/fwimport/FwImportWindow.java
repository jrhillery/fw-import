/*
 * Created on Dec 14, 2017
 */
package com.moneydance.modules.features.fwimport;

import static java.time.format.FormatStyle.MEDIUM;
import static java.time.format.TextStyle.SHORT_STANDALONE;
import static javax.swing.GroupLayout.DEFAULT_SIZE;
import static javax.swing.GroupLayout.PREFERRED_SIZE;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ResourceBundle;

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

import com.leastlogic.mdimport.util.CsvProcessWindow;
import com.leastlogic.swing.util.HTMLPane;

public class FwImportWindow extends JFrame implements ActionListener, PropertyChangeListener, CsvProcessWindow {
	private Main feature;
	private JFormattedTextField txtFileToImport;
	private JButton btnChooseFile;
	private JFormattedTextField txtMarketDate;
	private JLabel lblDayOfWeek;
	private JButton btnPriorDay;
	private JButton btnNextDay;
	private JButton btnImport;
	private JButton btnCommit;
	private HTMLPane pnOutputLog;

	static final String baseMessageBundleName = "com.moneydance.modules.features.fwimport.FwImportMessages"; //$NON-NLS-1$
	private static final ResourceBundle msgBundle = ResourceBundle.getBundle(baseMessageBundleName);
	private static final String FILE_NAME_PREFIX = "Portfolio_Position_"; //$NON-NLS-1$
	private static final String CHOOSER_TITLE = msgBundle.getString("FwImportWindow.chooser.title"); //$NON-NLS-1$
	private static final DateTimeFormatter textFieldDateFmt = DateTimeFormatter.ofLocalizedDate(MEDIUM);
	private static final DateTimeFormatter fileNameDateFmt = DateTimeFormatter.ofPattern("MMM-d-yyyy"); //$NON-NLS-1$
	private static final long serialVersionUID = -8092210194674298755L;

	/**
	 * Create the frame.
	 *
	 * @param feature
	 */
	public FwImportWindow(Main feature) {
		super(msgBundle.getString("FwImportWindow.window.title")); //$NON-NLS-1$
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

		JLabel lblFileToImport = new JLabel(msgBundle.getString("FwImportWindow.lblFileToImport.text")); //$NON-NLS-1$

		DefaultFormatter formatter = new DefaultFormatter();
		formatter.setOverwriteMode(false);
		this.txtFileToImport = new JFormattedTextField(formatter);
		this.txtFileToImport.setToolTipText(msgBundle.getString("FwImportWindow.txtFileToImport.toolTipText")); //$NON-NLS-1$
		this.txtFileToImport.setText('[' + CHOOSER_TITLE + ']');

		this.btnChooseFile = new JButton(msgBundle.getString("FwImportWindow.btnChooseFile.text")); //$NON-NLS-1$
		reducePreferredHeight(this.btnChooseFile);
		this.btnChooseFile.setToolTipText(msgBundle.getString("FwImportWindow.btnChooseFile.toolTipText")); //$NON-NLS-1$

		JLabel lblMarketDate = new JLabel(msgBundle.getString("FwImportWindow.lblMarketDate.text")); //$NON-NLS-1$

		this.txtMarketDate = new JFormattedTextField(textFieldDateFmt.toFormat());
		this.txtMarketDate.setToolTipText(msgBundle.getString("FwImportWindow.txtMarketDate.toolTipText")); //$NON-NLS-1$
		LocalDate today = LocalDate.now();
		setMarketDate(today);

		this.lblDayOfWeek = new JLabel();
		this.lblDayOfWeek.setFont(this.lblDayOfWeek.getFont()
			.deriveFont(this.lblDayOfWeek.getFont().getStyle() & ~Font.BOLD));
		setDayOfWeek(today);

		this.btnPriorDay = new JButton("<"); //$NON-NLS-1$
		reducePreferredHeight(this.btnPriorDay);
		this.btnPriorDay.setToolTipText(msgBundle.getString("FwImportWindow.btnPriorDay.toolTipText")); //$NON-NLS-1$

		this.btnNextDay = new JButton(">"); //$NON-NLS-1$
		reducePreferredHeight(this.btnNextDay);
		this.btnNextDay.setToolTipText(msgBundle.getString("FwImportWindow.btnNextDay.toolTipText")); //$NON-NLS-1$

		this.btnImport = new JButton(msgBundle.getString("FwImportWindow.btnImport.text")); //$NON-NLS-1$
		this.btnImport.setEnabled(false);
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
					.addComponent(lblMarketDate)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(this.txtMarketDate, PREFERRED_SIZE, 86, PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(this.lblDayOfWeek, PREFERRED_SIZE, 34, PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(this.btnPriorDay)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(this.btnNextDay)
					.addPreferredGap(ComponentPlacement.RELATED, 94, Short.MAX_VALUE)
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
						.addComponent(lblMarketDate)
						.addComponent(this.txtMarketDate, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
						.addComponent(this.lblDayOfWeek)
						.addComponent(this.btnPriorDay)
						.addComponent(this.btnNextDay)
						.addComponent(this.btnImport)
						.addComponent(this.btnCommit))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(scrollPane, DEFAULT_SIZE, 235, Short.MAX_VALUE))
		);
		gl_contentPane.linkSize(SwingConstants.HORIZONTAL, new Component[] {lblFileToImport, lblMarketDate});
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
		this.txtFileToImport.addPropertyChangeListener("value", this); //$NON-NLS-1$
		this.btnChooseFile.addActionListener(this);
		this.txtMarketDate.addPropertyChangeListener("value", this); //$NON-NLS-1$
		this.btnPriorDay.addActionListener(this);
		this.btnNextDay.addActionListener(this);
		this.btnImport.addActionListener(this);
		this.btnCommit.addActionListener(this);

	} // end wireEvents()

	/**
	 * Read in and set our icon image.
	 */
	private void readIconImage() {
		setIconImage(HTMLPane.readResourceImage("flat-funnel-32.png", getClass())); //$NON-NLS-1$

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
					new File(System.getenv("HOMEPATH"), "Downloads")); //$NON-NLS-1$ //$NON-NLS-2$
			chooser.setDialogTitle(CHOOSER_TITLE);
			chooser.setApproveButtonToolTipText(msgBundle.getString("FwImportWindow.approve.toolTipText")); //$NON-NLS-1$
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setFileFilter(new FileNameExtensionFilter(msgBundle.getString("FwImportWindow.csv.text"), "csv")); //$NON-NLS-1$ //$NON-NLS-2$
			int result = chooser.showDialog(getRootPane(), msgBundle.getString("FwImportWindow.approve.text")); //$NON-NLS-1$

			if (result == JFileChooser.APPROVE_OPTION) {
				setFileToImport(chooser.getSelectedFile());
			}
		}

		if (source == this.btnPriorDay) {
			LocalDate curDate = getMarketDate();

			if (curDate != null) {
				setMarketDate(curDate.minusDays(1));
			}
		}

		if (source == this.btnNextDay) {
			LocalDate curDate = getMarketDate();

			if (curDate != null) {
				setMarketDate(curDate.plusDays(1));
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
	 * This method gets called when a bound property is changed.
	 * @param evt a PropertyChangeEvent object describing the event source and the property that has changed.
	 */
	public void propertyChange(PropertyChangeEvent evt) {
		Object source = evt.getSource();

		if (source == this.txtFileToImport) {
			File fileToImport = getFileToImport();
			LocalDate localDate = parseFileNameAsMarketDate(fileToImport.getName());

			if (localDate != null) {
				setMarketDate(localDate.minusDays(1));
			}
			this.btnImport.setEnabled(true);
		}

		if (source == this.txtMarketDate) {
			LocalDate marketDate = getMarketDate();

			if (marketDate != null) {
				setDayOfWeek(marketDate);
			}
		}

	} // end propertyChange(PropertyChangeEvent)

	/**
	 * @return the file selected to import
	 */
	public File getFileToImport() {
		String fileToImport = (String) this.txtFileToImport.getValue();

		return fileToImport == null ? new File("") : new File(fileToImport); //$NON-NLS-1$
	} // end getFileToImport()

	/**
	 * @param file
	 */
	private void setFileToImport(File file) {
		this.txtFileToImport.setValue(file.getPath());

	} // end setFileToImport(File)

	/**
	 * @return the selected market date
	 */
	public LocalDate getMarketDate() {
		TemporalAccessor dateAcc = (TemporalAccessor) this.txtMarketDate.getValue();

		return asLocalDate(dateAcc);
	} // end getMarketDate()

	/**
	 * @param localDate
	 */
	private void setMarketDate(LocalDate localDate) {
		this.txtMarketDate.setValue(localDate);

	} // end setMarketDate(LocalDate)

	/**
	 * @param marketDate
	 */
	private void setDayOfWeek(LocalDate marketDate) {
		this.lblDayOfWeek.setText(
			'(' + marketDate.getDayOfWeek().getDisplayName(SHORT_STANDALONE, getLocale()) + ')');

	} // end setDayOfWeek(LocalDate)

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
	 * @param fileName
	 * @return the date encoded in the file name, if any
	 */
	private LocalDate parseFileNameAsMarketDate(String fileName) {
		LocalDate localDate = null;
		int dotPos = fileName.indexOf('.');

		if (fileName.startsWith(FILE_NAME_PREFIX) && dotPos > 0) {
			String dateStr = fileName.substring(FILE_NAME_PREFIX.length(), dotPos);
			try {
				localDate = asLocalDate(fileNameDateFmt.parse(dateStr));
			} catch (Exception e) {
				// ignore parsing problems
			}
		}

		return localDate;
	} // end parseFileNameAsMarketDate(String)

	/**
	 * @param dateAcc
	 * @return the referenced local date
	 */
	private LocalDate asLocalDate(TemporalAccessor dateAcc) {
		LocalDate localDate = dateAcc == null ? null
				: dateAcc.query(TemporalQueries.localDate());

		return localDate;
	} // end asLocalDate(TemporalAccessor)

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
	public FwImportWindow goAway() {
		Dimension winSize = getSize();
		System.err.format(getLocale(), "Closing %s with width=%.0f, height=%.0f.%n",
			getTitle(), winSize.getWidth(), winSize.getHeight());
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
					FwImportWindow frame = new FwImportWindow(null);
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

	} // end main(String[])

} // end class FwImportWindow
