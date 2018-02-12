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
import java.util.LinkedHashMap;
import java.util.ResourceBundle;

import com.infinitekind.moneydance.model.AccountBook;
import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyTable;
import com.infinitekind.moneydance.model.CurrencyType;
import com.leastlogic.mdimport.util.CsvProcessor;
import com.leastlogic.mdimport.util.SecurityHandler;
import com.leastlogic.moneydance.util.MdUtil;
import com.leastlogic.moneydance.util.MduException;

/**
 * Module used to import Yahoo quote data into Moneydance.
 */
public class YqImporter extends CsvProcessor {
	private CurrencyTable securities;

	private LinkedHashMap<CurrencyType, SecurityHandler> priceChanges = new LinkedHashMap<>();
	private int numPricesSet = 0;
	private ResourceBundle msgBundle = null;

	private static final String propertiesFileName = "yq-import.properties";
	private static final DateTimeFormatter marketDateFmt = DateTimeFormatter.ofPattern("yyyy/M/d");

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
		CurrencyType security = this.securities.getCurrencyByTickerSymbol(getCol("col.ticker"));

		if (security == null) {
			System.err.format("No Moneydance security for ticker symbol [%s].",
				getCol("col.ticker"));
		} else {
			storePriceQuoteIfDiff(security);
		}

	} // end processRow()

	/**
	 * @param security The Moneydance security to use
	 */
	private void storePriceQuoteIfDiff(CurrencyType security) throws MduException {
		BigDecimal price = new BigDecimal(getCol("col.price"));

		int importDate = parseDate(getCol("col.date"));
		CurrencySnapshot snapshot = MdUtil.getSnapshotForDate(security, importDate);
		double newPrice = price.doubleValue();
		double oldPrice = MdUtil.convRateToPrice(snapshot.getUserRate());

		// store this quote if it differs and we don't already have this security
		if ((importDate != snapshot.getDateInt() || newPrice != oldPrice)
				&& !this.priceChanges.containsKey(security)) {
			// Change %s (%s) price from %s to %s (<span class="%s">%+.2f%%</span>).
			NumberFormat priceFmt = getCurrencyFormat(price);
			String spanCl = newPrice < oldPrice ? CL_DECREASE
				: newPrice > oldPrice ? CL_INCREASE : "";
			writeFormatted("YQIMP03", security.getName(), security.getTickerSymbol(),
				priceFmt.format(oldPrice), priceFmt.format(newPrice), spanCl,
				(newPrice / oldPrice - 1) * 100);

			storePriceUpdate(security, newPrice, importDate);
			++this.numPricesSet;
		}

	} // end storePriceQuoteIfDiff(CurrencyType)

	/**
	 * @param security The Moneydance security to update
	 * @param newPrice Price quote
	 * @param importDate Market date integer
	 */
	private void storePriceUpdate(CurrencyType security, double newPrice, int importDate)
			throws MduException {
		SecurityHandler securityHandler = new SecurityHandler(security);
		String highPrice = getCol("col.high");
		String lowPrice = getCol("col.low");
		String volume = getCol("col.vol");

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
		addHandler(securityHandler);

	} // end storePriceUpdate(CurrencyType, double, int)

	/**
	 * @param marketDate The date string to parse
	 * @return The numeric date value in decimal form YYYYMMDD
	 */
	private int parseDate(String marketDate) throws MduException {
		LocalDate lDate;
		try {
			lDate = LocalDate.parse(marketDate, marketDateFmt);
		} catch (Exception e) {
			// Exception parsing date from [%s]. %s
			throw asException(e, "YQIMP17", marketDate, e.toString());
		}

		return MdUtil.convLocalToDateInt(lDate);
	} // end parseDate(String)

	/**
	 * Add a security handler to our collection.
	 *
	 * @param handler
	 */
	private void addHandler(SecurityHandler handler) {
		this.priceChanges.put(handler.getSecurity(), handler);

	} // end addHandler(SecurityHandler)

	/**
	 * Commit any changes to Moneydance.
	 */
	public void commitChanges() {
		for (SecurityHandler sHandler : this.priceChanges.values()) {
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
	 * @return True when we have uncommitted changes in memory
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
	 * @return Our message bundle
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
	 * @return Message for this key
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
