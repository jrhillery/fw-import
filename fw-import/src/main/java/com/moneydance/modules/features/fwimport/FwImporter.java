/*
 * Created on Dec 16, 2017
 */
package com.moneydance.modules.features.fwimport;

import com.infinitekind.moneydance.model.*;
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
 * Data record to hold an imported row.
 *
 * @param accountNumber Account name or number
 * @param ticker        Ticker symbol
 * @param securityName  Security name or description
 * @param shares        Quantity of shares
 * @param price         Price
 * @param balance       Balance or value
 * @param effectiveDate Effective date
 */
record RowRec(
	String accountNumber,
	String ticker,
	String securityName,
	BigDecimal shares,
	BigDecimal price,
	BigDecimal balance,
	LocalDate effectiveDate) {

} // end record RowRec

/**
 * Module used to import Fidelity NetBenefits workplace account data into
 * Moneydance.
 */
public class FwImporter extends CsvProcessor implements StagedInterface {
	private final Account root;
	private final CurrencyTable securities;

	private final LinkedHashMap<CurrencyType, SecurityHandler> priceChanges = new LinkedHashMap<>();
	private final LinkedHashSet<LocalDate> dates = new LinkedHashSet<>();

	private static final String propertiesFileName = "fw-import.properties";
	private static final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("E MMM d, y");

	/**
	 * Sole constructor.
	 *
	 * @param importWindow Our import console
	 * @param accountBook  Moneydance account book
	 */
	public FwImporter(FwImportWindow importWindow, AccountBook accountBook) {
		super(importWindow, propertiesFileName);
		this.root = accountBook.getRootAccount();
		this.securities = accountBook.getCurrencies();
		importWindow.setStaged(this);

	} // end (FwImportWindow, AccountBook) constructor

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
	 * Retrieve data from columns in current row
	 * @return Populated RowRec instance
	 */
	private RowRec importRow() throws MduException {
		return new RowRec(
			getCol("col.account.num"),
			getCol("col.ticker"),
			getCol("col.name"),
			new BigDecimal(getCol("col.shares")),
			new BigDecimal(getCol("col.price")),
			new BigDecimal(getCol("col.value")),
			LocalDate.parse(getCol("col.date")));
	} // end importRow()

	/**
	 * Import this row of the comma separated value file.
	 */
	protected void processRow() throws MduException {
		RowRec imp = importRow();
		Optional<Account> account =
			MdUtil.getSubAccountByInvestNumber(this.root, imp.accountNumber());

		if (account.isEmpty()) {
			this.impWin.addText("Unable to obtain Moneydance investment account with number [%s]"
				.formatted(imp.accountNumber()));
		}
		CurrencyType security = this.securities.getCurrencyByTickerSymbol(imp.ticker());

		if (security == null) {
            account.ifPresent(subAcct -> verifyAccountBalance(subAcct, imp));
		} else {
			storePriceQuoteIfDiff(security, imp.price(), imp.effectiveDate());

			account.ifPresent(subAcct -> verifyShareBalance(subAcct, security, imp.shares()));
		}
		this.dates.add(imp.effectiveDate());

	} // end processRow()

	/**
	 * @param security      The Moneydance security to use
	 * @param price         Price found during import
	 * @param effectiveDate Effective date for quote
	 */
	private void storePriceQuoteIfDiff(CurrencyType security, BigDecimal price,
									   LocalDate effectiveDate) {
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

			addHandler(new SecurityHandler(ssList).storeNewPrice(newPrice, effDateInt));
		}

	} // end storePriceQuoteIfDiff(CurrencyType, BigDecimal, LocalDate)

	/**
	 * @param account Moneydance account
	 * @param imp     Imported record from current row
	 */
	private void verifyAccountBalance(Account account, RowRec imp) {
		BigDecimal balance = MdUtil.getCurrentBalance(account);

		if (imp.balance().compareTo(balance) != 0) {
			NumberFormat cf = MdUtil.getCurrencyFormat(this.locale, balance, imp.balance());
			this.impWin.addText(("Found a different balance in account %s: have %s, imported %s;"
				+ " Note: No Moneydance security for ticker symbol [%s] (%s)")
				.formatted(account.getAccountName(), cf.format(balance),
				cf.format(imp.balance()), imp.ticker(), imp.securityName()));
		}

	} // end verifyAccountBalance(Account, RowRec)

	/**
	 * @param account        Moneydance account
	 * @param sec            The Moneydance security to use
	 * @param importedShares Shares found during import
	 */
	private void verifyShareBalance(Account account, CurrencyType sec,
									BigDecimal importedShares) {
		MdUtil.getSubAccountByName(account, sec.getName()).ifPresentOrElse(secAccount -> {
			BigDecimal balance = MdUtil.getCurrentBalance(secAccount);

			if (importedShares.compareTo(balance) != 0) {
				NumberFormat nf = MdUtil.getNumberFormat(this.locale, balance, importedShares);
				this.impWin.addText(
					"Found a different %s (%s) share balance in account %s: have %s, imported %s"
					.formatted(secAccount.getAccountName(), sec.getTickerSymbol(),
					account.getAccountName(), nf.format(balance), nf.format(importedShares)));
			}
		},
		() -> this.impWin.addText("Unable to obtain Moneydance security [%s (%s)] in account %s"
			.formatted(sec.getName(), sec.getTickerSymbol(), account.getAccountName())));

	} // end verifyShareBalance(Account, CurrencyType, BigDecimal)

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
	 *
	 * @return Optional summary of the changes committed
	 */
	public Optional<String> commitChanges() {
		int numPricesSet = this.priceChanges.size();

		this.priceChanges.forEach((security, sHandler) -> sHandler.applyUpdate());
		forgetChanges();

		return Optional.of("Changed %d security price%s"
			.formatted(numPricesSet, numPricesSet == 1 ? "" : "s"));
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

} // end class FwImporter
