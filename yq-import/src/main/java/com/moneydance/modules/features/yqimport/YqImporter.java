/*
 * Created on Jan 17, 2018
 */
package com.moneydance.modules.features.yqimport;

import com.infinitekind.moneydance.model.AccountBook;
import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyTable;
import com.infinitekind.moneydance.model.CurrencyType;
import com.leastlogic.mdimport.util.CsvProcessor;
import com.leastlogic.moneydance.util.*;
import com.leastlogic.swing.util.HTMLPane;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Module used to import Yahoo quote data into Moneydance.
 */
public class YqImporter extends CsvProcessor {
	private final CurrencyTable securities;

	private final LinkedHashMap<CurrencyType, SecurityHandler> priceChanges = new LinkedHashMap<>();
	private final LinkedHashSet<LocalDate> dates = new LinkedHashSet<>();

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
		this.impWin.addText("Importing price data from file %s"
			.formatted(this.impWin.getFileToImport().getFileName()));

		processFile();
		this.impWin.addText("Found effective date%s %s".formatted(this.dates.size() == 1 ? "" : "s",
			this.dates.stream().map(dt -> dt.format(dateFmt)).collect(Collectors.joining("; "))));

		if (!isModified()) {
			this.impWin.addText("No new price data found");
		}

	} // end importFile()

	/**
	 * Import this row of the comma separated value file.
	 */
	protected void processRow() throws MduException {
		CurrencyType security = this.securities.getCurrencyByTickerSymbol(getCol("col.ticker"));
		LocalDate effectiveDate = parseDate(getCol("col.date"));

		if (security == null) {
			MdLog.all("No Moneydance security for ticker symbol [%s]"
					.formatted(getCol("col.ticker")));
		} else {
			storePriceQuoteIfDiff(security, effectiveDate);
		}
		this.dates.add(effectiveDate);

	} // end processRow()

	/**
	 * @param security      The Moneydance security to use
	 * @param effectiveDate Effective date for quote
	 */
	private void storePriceQuoteIfDiff(CurrencyType security, LocalDate effectiveDate) throws MduException {
		BigDecimal price = new BigDecimal(getCol("col.price"));

		int effDateInt = MdUtil.convLocalToDateInt(effectiveDate);
		SnapshotList ssList = new SnapshotList(security);
		Optional<CurrencySnapshot> snapshot = ssList.getSnapshotForDate(effDateInt);
		BigDecimal oldPrice = snapshot.map(ss ->
			MdUtil.getAndValidateCurrentSnapshotPrice(security, ss, this.locale, this.impWin::addText))
			.orElse(BigDecimal.ONE);

		// store this quote if it differs and we don't already have this security
		if ((snapshot.isEmpty() || effDateInt != snapshot.get().getDateInt()
				|| price.compareTo(oldPrice) != 0) && !this.priceChanges.containsKey(security)) {
			NumberFormat priceFmt = MdUtil.getCurrencyFormat(this.locale, oldPrice, price);
			double newPrice = price.doubleValue();
			this.impWin.addText("Change %s (%s) price from %s to %s (<span class=\"%s\">%+.2f%%</span>)"
				.formatted(security.getName(), security.getTickerSymbol(),
				priceFmt.format(oldPrice), priceFmt.format(newPrice),
				HTMLPane.getSpanCl(price, oldPrice), (newPrice / oldPrice.doubleValue() - 1) * 100));

			storePriceUpdate(ssList, newPrice, effDateInt);
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
				this.impWin.addText(
					"Exception parsing quote data (volume [%s], high [%s], low [%s]): %s"
					.formatted(volume, highPrice, lowPrice, e.toString()));
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
		LocalDate mktDate;
		try {
			mktDate = LocalDate.parse(marketDate, marketDateFmt);
		} catch (Exception e) {
			throw new MduException(e, "Exception parsing date from [%s]: %s", marketDate, e);
		}

		return mktDate;
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
		int numPricesSet = this.priceChanges.size();
		this.priceChanges.forEach((security, sHandler) -> sHandler.applyUpdate());

		this.impWin.addText("Changed %d security price%s"
			.formatted(numPricesSet, numPricesSet == 1 ? "" : "s"));

		forgetChanges();

	} // end commitChanges()

	/**
	 * Clear out any pending changes.
	 */
	public void forgetChanges() {
		this.priceChanges.clear();
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

} // end class YqImporter
