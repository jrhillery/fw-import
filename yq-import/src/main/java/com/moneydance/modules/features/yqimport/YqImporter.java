/*
 * Created on Jan 17, 2018
 */
package com.moneydance.modules.features.yqimport;

import static com.leastlogic.swing.util.HTMLPane.CL_DECREASE;
import static com.leastlogic.swing.util.HTMLPane.CL_INCREASE;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ResourceBundle;

import com.infinitekind.moneydance.model.AccountBook;
import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyTable;
import com.infinitekind.moneydance.model.CurrencyType;
import com.leastlogic.moneydance.util.CsvProcessor;
import com.leastlogic.moneydance.util.MdUtil;
import com.leastlogic.moneydance.util.MduException;
import com.leastlogic.moneydance.util.SecurityHandler;
import com.leastlogic.moneydance.util.SecurityHandlerCollector;

/**
 * Module used to import Yahoo quote data into Moneydance.
 */
public class YqImporter extends CsvProcessor implements SecurityHandlerCollector {
	private CurrencyTable securities;

	private ArrayList<SecurityHandler> priceChanges = new ArrayList<>();
	private int numPricesSet = 0;
	private ResourceBundle msgBundle = null;

	private static final String propertiesFileName = "yq-import.properties";
	private static final DateTimeFormatter marketDateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d");

	/**
	 * Sole constructor.
	 *
	 * @param importWindow
	 * @param accountBook Moneydance account book
	 */
	public YqImporter(YqImportWindow importWindow, AccountBook accountBook) {
		super(importWindow, propertiesFileName);
		this.securities = accountBook.getCurrencies();

	} // end (YqImportWindow, AccountBook) constructor

	/**
	 * Import the selected comma separated value file.
	 */
	public void importFile() throws MduException {
		// Importing price data from file %s.
		writeFormatted("YQIMP01", this.importWindow.getFileToImport().getName());

		processFile();

		if (!isModified()) {
			// No new price data found in %s.
			writeFormatted("YQIMP08", this.importWindow.getFileToImport().getName());
		}

	} // end importFile()

	/**
	 * Import this row of the comma separated value file.
	 */
	protected void processRow() throws MduException {
		CurrencyType security = this.securities
			.getCurrencyByTickerSymbol(stripQuotes("col.ticker"));

		if (security != null) {
			storePriceQuoteIfDiff(security);
		} else {
			System.err.format("No Moneydance security for ticker symbol [%s].",
				stripQuotes("col.ticker"));
		}

	} // end processRow()

	/**
	 * @param security the Moneydance security to use
	 */
	private void storePriceQuoteIfDiff(CurrencyType security) throws MduException {
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
	private int parseDate(String qDate) throws MduException {
		LocalDate lDate;
		try {
			lDate = LocalDate.parse(qDate, marketDateFormatter);
		} catch (Exception e) {
			// Exception parsing date from [%s]. %s
			throw asException(e, "YQIMP17", qDate, e.toString());
		}

		return MdUtil.convLocalToDateInt(lDate);
	} // end parseDate(String)

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
	 * @return our message bundle
	 */
	private ResourceBundle getMsgBundle() {
		if (this.msgBundle == null) {
			this.msgBundle = MdUtil.getMsgBundle(YqImportWindow.baseMessageBundleName,
				this.locale);
		}

		return this.msgBundle;
	} // end getMsgBundle()

	/**
	 * @param cause Exception that caused this (null if none)
	 * @param key The resource bundle key (or message)
	 * @param params Optional parameters for the detail message
	 */
	private MduException asException(Throwable cause, String key, Object... params) {

		return new MduException(cause, retrieveMessage(key), params);
	} // end asException(Throwable, String, Object...)

	/**
	 * @param key The resource bundle key (or message)
	 * @return message for this key
	 */
	private String retrieveMessage(String key) {
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

} // end class YqImporter
