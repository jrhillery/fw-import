/*
 * Created on Jan 17, 2018
 */
package com.moneydance.modules.features.yqimport;

import static com.johns.swing.util.HTMLPane.CL_DECREASE;
import static com.johns.swing.util.HTMLPane.CL_INCREASE;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import com.infinitekind.moneydance.model.AccountBook;
import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyTable;
import com.infinitekind.moneydance.model.CurrencyType;
import com.johns.moneydance.util.MdUtil;
import com.johns.moneydance.util.SecurityHandler;
import com.johns.moneydance.util.SecurityHandlerCollector;

/**
 * Module used to import Yahoo quote data into Moneydance.
 */
public class YqImporter implements SecurityHandlerCollector {
	private YqImportWindow importWindow;
	private Locale locale;
	private CurrencyTable securities;

	private List<SecurityHandler> priceChanges = new ArrayList<>();
	private int numPricesSet = 0;
	private Map<String, String> csvRowMap = new LinkedHashMap<>();
	private Properties yqImportProps = null;

	private static ResourceBundle msgBundle = null;

	private static final String propertiesFileName = "yq-import.properties";
	private static final String baseMessageBundleName = "com.moneydance.modules.features.yqimport.YqImportMessages";
	private static final char DOUBLE_QUOTE = '"';
	private static final DateTimeFormatter marketDateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d");

	/**
	 * Sole constructor.
	 *
	 * @param importWindow
	 * @param accountBook Moneydance account book
	 */
	public YqImporter(YqImportWindow importWindow, AccountBook accountBook) {
		this.importWindow = importWindow;
		this.locale = importWindow.getLocale();
		this.securities = accountBook.getCurrencies();

	} // end (YqImportWindow, AccountBook) constructor

	/**
	 * Import the selected comma separated value file.
	 */
	public void importFile() throws YqiException {
		// Importing data from file %s.
		writeFormatted("YQIMP01", this.importWindow.getFileToImport().getName());

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

					importRow();
				}
			} // end while
		} finally {
			close(reader);
		}
		if (!isModified()) {
			// No new price data found in %s.
			writeFormatted("YQIMP08", this.importWindow.getFileToImport().getName());
		}

	} // end importFile()

	/**
	 * Import this row of the comma separated value file.
	 */
	private void importRow() throws YqiException {
		CurrencyType security = this.securities
			.getCurrencyByTickerSymbol(stripQuotes("col.ticker"));

		if (security != null) {
			storePriceQuoteIfDiff(security);
		} else {
			System.err.format("No Moneydance security for ticker symbol [%s].",
				stripQuotes("col.ticker"));
		}

	} // end importRow()

	/**
	 * @param security the Moneydance security to use
	 */
	private void storePriceQuoteIfDiff(CurrencyType security) throws YqiException {
		BigDecimal price = new BigDecimal(stripQuotes("col.price"));

		NumberFormat priceFmt = getCurrencyFormat(price);
		int importDate = parseDate(stripQuotes("col.date"));
		CurrencySnapshot latestSnapshot = MdUtil.getLatestSnapshot(security);
		MdUtil.validateCurrentUserRate(security, latestSnapshot, priceFmt);
		double newPrice = price.doubleValue();
		double oldPrice = MdUtil.convRateToPrice(importDate < latestSnapshot.getDateInt()
			? security.getUserRateByDateInt(importDate)
			: latestSnapshot.getUserRate());

		if (importDate != latestSnapshot.getDateInt() || newPrice != oldPrice) {
			// Change %s price from %s to %s (<span class="%s">%+.2f%%</span>).
			String spanCl = newPrice < oldPrice ? CL_DECREASE
				: newPrice > oldPrice ? CL_INCREASE : "";
			writeFormatted("YQIMP03", security.getName(), priceFmt.format(oldPrice),
				priceFmt.format(newPrice), spanCl, (newPrice / oldPrice - 1) * 100);

			SecurityHandler securityHandler = new SecurityHandler(security, this);
			String highPrice = stripQuotes("col.high");
			String lowPrice = stripQuotes("col.low");
			String volume = stripQuotes("col.vol");

			if (highPrice.length() > 0 && lowPrice.length() > 0 && volume.length() > 0) {
				try {
					securityHandler.storeNewPrice(newPrice, importDate, Long.parseLong(volume),
						Double.parseDouble(highPrice), Double.parseDouble(lowPrice));
				} catch (Exception e) {
					// Exception parsing quote data (volume [%s], high [%s], low [%s]). %s
					writeFormatted("YQIMP18", volume, highPrice, lowPrice, e.toString());
					securityHandler.storeNewPrice(newPrice, importDate);
				}
			} else {
				securityHandler.storeNewPrice(newPrice, importDate);
			}
			++this.numPricesSet;
		}

	} // end storePriceQuoteIfDiff(CurrencyType, BigDecimal)

	/**
	 * @param qDate
	 * @return the numeric date value in decimal form YYYYMMDD
	 */
	private int parseDate(String qDate) throws YqiException {
		LocalDate lDate;
		try {
			lDate = LocalDate.parse(qDate, marketDateFormatter);
		} catch (Exception e) {
			// Exception parsing date from [%s]. %s
			throw new YqiException(e, "YQIMP17", qDate, e.toString());
		}

		return MdUtil.convLocalToDateInt(lDate);
	} // end parseDate(String)

	/**
	 * @param propKey property key for column header
	 * @return value from the csv row map with any surrounding double quotes removed
	 */
	private String stripQuotes(String propKey) throws YqiException {
		String csvColumnKey = getYqImportProps().getProperty(propKey);
		String val = this.csvRowMap.get(csvColumnKey);
		if (val == null) {
			// Unable to locate column %s (%s) in %s. Found columns %s
			throw new YqiException(null, "YQIMP11", csvColumnKey, propKey,
				this.importWindow.getFileToImport(), this.csvRowMap.keySet());
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
	} // end stripQuotes(String)

	/**
	 * @return a buffered reader to read from the file selected to import
	 */
	private BufferedReader openFile() {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(this.importWindow.getFileToImport()));
		} catch (Exception e) {
			// Exception opening file %s. %s
			writeFormatted("YQIMP12", this.importWindow.getFileToImport(), e);
		}

		return reader;
	} // end openFile()

	/**
	 * @param reader
	 * @return true when the next read will not block for input, false otherwise
	 */
	private boolean hasMore(BufferedReader reader) throws YqiException {
		try {

			return reader.ready();
		} catch (Exception e) {
			// Exception checking file %s.
			throw new YqiException(e, "YQIMP13", this.importWindow.getFileToImport());
		}
	} // end hasMore(BufferedReader)

	/**
	 * @param reader
	 * @return the comma separated tokens from the next line in the file
	 */
	private String[] readLine(BufferedReader reader) throws YqiException {
		try {
			String line = reader.readLine();

			return line == null ? null : line.split(",");
		} catch (Exception e) {
			// Exception reading from file %s.
			throw new YqiException(e, "YQIMP14", this.importWindow.getFileToImport());
		}
	} // end readLine(BufferedReader)

	/**
	 * Close the specified reader, ignoring any exceptions.
	 *
	 * @param reader
	 */
	private static void close(BufferedReader reader) {
		try {
			reader.close();
		} catch (Exception e) { /* ignore */ }

	} // end close(BufferedReader)

	/**
	 * Add a security handler to our collection.
	 *
	 * @param handler
	 */
	public void addHandler(SecurityHandler handler) {
		this.priceChanges.add(handler);

	} // end addHandler(SecurityHandler)

	/**
	 * Commit any changes to Moneydance.
	 */
	public void commitChanges() {
		for (SecurityHandler sHandler : this.priceChanges) {
			sHandler.applyUpdate();
		}
		// Changed %d security price%s.
		writeFormatted("YQIMP07", this.numPricesSet, this.numPricesSet == 1 ? "" : "s");

		forgetChanges();

	} // end commitChanges()

	/**
	 * Clear out any pending changes.
	 */
	public void forgetChanges() {
		this.priceChanges.clear();
		this.numPricesSet = 0;

	} // end forgetChanges()

	/**
	 * @return true when we have uncommitted changes in memory
	 */
	public boolean isModified() {

		return !this.priceChanges.isEmpty();
	} // end isModified()

	/**
	 * Release any resources we acquired.
	 *
	 * @return null
	 */
	public YqImporter releaseResources() {
		// nothing to release

		return null;
	} // end releaseResources()

	/**
	 * @return our properties
	 */
	private Properties getYqImportProps() throws YqiException {
		if (this.yqImportProps == null) {
			InputStream propsStream = getClass().getClassLoader()
				.getResourceAsStream(propertiesFileName);
			if (propsStream == null)
				// Unable to find %s on the class path.
				throw new YqiException(null, "YQIMP15", propertiesFileName);

			this.yqImportProps = new Properties();
			try {
				this.yqImportProps.load(propsStream);
			} catch (Exception e) {
				this.yqImportProps = null;

				// Exception loading %s.
				throw new YqiException(e, "YQIMP16", propertiesFileName);
			} finally {
				try {
					propsStream.close();
				} catch (Exception e) { /* ignore */ }
			}
		}

		return this.yqImportProps;
	} // end getYqImportProps()

	/**
	 * @return our message bundle
	 */
	private static ResourceBundle getMsgBundle() {
		if (msgBundle == null) {
			msgBundle = MdUtil.getMsgBundle(baseMessageBundleName);
		}

		return msgBundle;
	} // end getMsgBundle()

	/**
	 * Inner class to house exceptions.
	 */
	public static class YqiException extends Exception {

		private static final long serialVersionUID = 7259141841165838995L;

		/**
		 * @param cause Exception that caused this (null if none)
		 * @param key The resource bundle key (or message)
		 * @param params Optional parameters for the detail message
		 */
		public YqiException(Throwable cause, String key, Object... params) {
			super(String.format(retrieveMessage(key), params), cause);

		} // end (Throwable, String, Object...) constructor

	} // end class YqiException

	/**
	 * @param key The resource bundle key (or message)
	 * @return message for this key
	 */
	private static String retrieveMessage(String key) {
		try {

			return getMsgBundle().getString(key);
		} catch (Exception e) {
			// just use the key when not found
			return key;
		}
	} // end retrieveMessage(String)

	/**
	 * @param key The resource bundle key (or message)
	 * @param params Optional array of parameters for the message
	 */
	private void writeFormatted(String key, Object... params) {
		this.importWindow.addText(String.format(this.locale, retrieveMessage(key), params));

	} // end writeFormatted(String, Object...)

	/**
	 * @param amount
	 * @return a currency number format with the number of fraction digits in amount
	 */
	private NumberFormat getCurrencyFormat(BigDecimal amount) {
		DecimalFormat formatter = (DecimalFormat) NumberFormat.getCurrencyInstance(this.locale);
		formatter.setMinimumFractionDigits(amount.scale());

		return formatter;
	} // end getCurrencyFormat(BigDecimal)

} // end class YqImporter
