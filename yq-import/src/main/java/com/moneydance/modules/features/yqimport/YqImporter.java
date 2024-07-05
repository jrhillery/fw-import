/*
 * Created on Jan 17, 2018
 */
package com.moneydance.modules.features.yqimport;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.infinitekind.moneydance.model.AccountBook;
import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyTable;
import com.infinitekind.moneydance.model.CurrencyType;
import com.leastlogic.mdimport.util.CsvProcessor;
import com.leastlogic.mdimport.util.SecurityHandler;
import com.leastlogic.moneydance.util.MdUtil;
import com.leastlogic.moneydance.util.MduException;
import com.leastlogic.moneydance.util.SnapshotList;
import com.leastlogic.swing.util.HTMLPane;

/**
 * Module used to import Yahoo quote data into Moneydance.
 */
public class YqImporter extends CsvProcessor {
	private final CurrencyTable securities;

	private final LinkedHashMap<CurrencyType, SecurityHandler> priceChanges = new LinkedHashMap<>();
	private int numPricesSet = 0;
	private final LinkedHashSet<LocalDate> dates = new LinkedHashSet<>();
	private ResourceBundle msgBundle = null;

	private static final String propertiesFileName = "yq-import.properties";
	private static final DateTimeFormatter marketDateFmt = DateTimeFormatter.ofPattern("yyyy/M/d");
	private static final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("E MMM d, y");

	/**
	 * Sole constructor.
	 *
	 * @param importWindow Our import console
	 * @param accountBook  Moneydance account book
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
		writeFormatted("YQIMP01", this.importWindow.getFileToImport().getFileName());

		processFile();
		// Found effective date%s %s.
		writeFormatted("YQIMP09", this.dates.size() == 1 ? "" : "s",
			this.dates.stream().map(dt -> dt.format(dateFmt)).collect(Collectors.joining("; ")));

		if (!isModified()) {
			// No new price data found.
			writeFormatted("YQIMP08");
		}

	} // end importFile()

	/**
	 * Import this row of the comma separated value file.
	 */
	protected void processRow() throws MduException {
		CurrencyType security = this.securities.getCurrencyByTickerSymbol(getCol("col.ticker"));
		LocalDate lDate = parseDate(getCol("col.date"));

		if (security == null) {
			System.err.format(this.locale, "No Moneydance security for ticker symbol [%s].%n",
				getCol("col.ticker"));
		} else {
			storePriceQuoteIfDiff(security, lDate);
		}
		this.dates.add(lDate);

	} // end processRow()

	/**
	 * @param security The Moneydance security to use
	 * @param lDate Effective date for quote
	 */
	private void storePriceQuoteIfDiff(CurrencyType security, LocalDate lDate) throws MduException {
		BigDecimal price = new BigDecimal(getCol("col.price"));

		int importDate = MdUtil.convLocalToDateInt(lDate);
		SnapshotList ssList = new SnapshotList(security);
		CurrencySnapshot snapshot = ssList.getSnapshotForDate(importDate);
		BigDecimal oldPrice = snapshot == null ? BigDecimal.ONE
				: MdUtil.convRateToPrice(snapshot.getRate());

		// store this quote if it differs, and we don't already have this security
		if ((snapshot == null || importDate != snapshot.getDateInt()
				|| price.compareTo(oldPrice) != 0) && !this.priceChanges.containsKey(security)) {
			// Change %s (%s) price from %s to %s (<span class="%s">%+.2f%%</span>).
			NumberFormat priceFmt = MdUtil.getCurrencyFormat(this.locale, price);
			double newPrice = price.doubleValue();
			writeFormatted("YQIMP03", security.getName(), security.getTickerSymbol(),
				priceFmt.format(oldPrice), priceFmt.format(newPrice),
				HTMLPane.getSpanCl(price, oldPrice), (newPrice / oldPrice.doubleValue() - 1) * 100);

			storePriceUpdate(ssList, newPrice, importDate);
			++this.numPricesSet;
		}

	} // end storePriceQuoteIfDiff(CurrencyType, LocalDate)

	/**
	 * @param snapshotList The list of snapshots to use for the Moneydance security to update
	 * @param newPrice Price quote
	 * @param importDate Market date integer
	 */
	private void storePriceUpdate(SnapshotList snapshotList, double newPrice, int importDate)
			throws MduException {
		SecurityHandler securityHandler = new SecurityHandler(snapshotList);
		String highPrice = getCol("col.high");
		String lowPrice = getCol("col.low");
		String volume = getCol("col.vol");

		if (!highPrice.isEmpty() && !lowPrice.isEmpty() && !volume.isEmpty()) {
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

	} // end storePriceUpdate(SnapshotList, double, int)

	/**
	 * @param marketDate The date string to parse
	 * @return A corresponding local date instance
	 */
	private LocalDate parseDate(String marketDate) throws MduException {
		LocalDate lDate;
		try {
			lDate = LocalDate.parse(marketDate, marketDateFmt);
		} catch (Exception e) {
			// Exception parsing date from [%s]. %s
			throw asException(e, "YQIMP17", marketDate, e.toString());
		}

		return lDate;
	} // end parseDate(String)

	/**
	 * Add a security handler to our collection.
	 *
	 * @param handler A deferred update security handler to store
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
		this.dates.clear();

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
	 * @param cause  Exception that caused this (null if none)
	 * @param key    The resource bundle key (or message)
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
