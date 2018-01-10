/*
 * Created on Dec 16, 2017
 */
package com.moneydance.modules.features.fwimport;

import static com.johns.swing.util.HTMLPane.CL_DECREASE;
import static com.johns.swing.util.HTMLPane.CL_INCREASE;
import static java.math.RoundingMode.HALF_EVEN;
import static java.time.format.FormatStyle.MEDIUM;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import com.infinitekind.moneydance.model.Account;
import com.infinitekind.moneydance.model.AccountBook;
import com.infinitekind.moneydance.model.AcctFilter;
import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyTable;
import com.infinitekind.moneydance.model.CurrencyType;

/**
 * Module used to import Fidelity NetBenefits workplace account data into
 * Moneydance.
 */
public class FwImporter {

	/**
	 * This object handles deferred updates to a Moneydance security.
	 */
	private class SecurityHandler {
		private CurrencyType security;

		private double newPrice = 0;
		private int newDate = 0;

		/**
		 * Sole constructor.
		 *
		 * @param security
		 */
		public SecurityHandler(CurrencyType security) {
			this.security = security;

		} // end (CurrencyType) constructor

		/**
		 * Store a deferred price quote for a specified date integer.
		 *
		 * @param newPrice price quote
		 * @param newDate date integer
		 */
		public void storeNewPrice(double newPrice, int newDate) {
			this.newPrice = newPrice;
			this.newDate = newDate;
			FwImporter.this.priceChanges.add(this);

		} // end storeNewPrice(double, int)

		/**
		 * Apply the stored update.
		 */
		public void applyUpdate() {
			CurrencySnapshot latestSnapshot = getLatestSnapshot(this.security);
			this.security.setSnapshotInt(this.newDate, 1 / this.newPrice);

			if (this.newDate >= latestSnapshot.getDateInt()) {
				this.security.setUserRate(1 / this.newPrice);
			}

		} // end applyUpdate()

		/**
		 * @return a string representation of this SecurityHandler
		 */
		public String toString() {

			return this.security.getTickerSymbol() + ":" + this.newPrice;
		} // end toString()

	} // end class SecurityHandler

	private FwImportWindow importWindow;
	private Locale locale;
	private Account root;
	private CurrencyTable securities;

	private List<SecurityHandler> priceChanges = new ArrayList<>();
	private int numPricesSet = 0;
	private Map<String, String> csvRowMap = new LinkedHashMap<>();
	private Properties fwImportProps = null;

	private static ResourceBundle msgBundle = null;

	private static final String propertiesFileName = "fw-import.properties";
	private static final String baseMessageBundleName = "com.moneydance.modules.features.fwimport.FwImportMessages";
	private static final char DOUBLE_QUOTE = '"';
	private static final double [] centMult = {1, 10, 100, 1000, 10000};
	private static final DateTimeFormatter dateFmt = DateTimeFormatter.ofLocalizedDate(MEDIUM);
	private static final int PRICE_FRACTION_DIGITS = 6;

	/**
	 * Sole constructor.
	 *
	 * @param importWindow
	 * @param accountBook Moneydance account book
	 */
	public FwImporter(FwImportWindow importWindow, AccountBook accountBook) {
		this.importWindow = importWindow;
		this.locale = importWindow.getLocale();
		this.root = accountBook.getRootAccount();
		this.securities = accountBook.getCurrencies();

	} // end (FwImportWindow, AccountBook) constructor

	/**
	 * Import the selected comma separated value file.
	 */
	public void importFile() throws FwiException {
		if (this.importWindow.getMarketDate() == null) {
			// Market date must be specified.
			writeFormatted("FWIMP00");
		} else {
			// Importing data for %s from file %s.
			writeFormatted("FWIMP01", this.importWindow.getMarketDate().format(dateFmt),
				this.importWindow.getFileToImport().getName());

			BufferedReader reader = openFile();
			if (reader == null)
				return; // nothing to import

			try {
				String[] header = readLine(reader);

				while (hasMore(reader)) {
					String[] values = readLine(reader);

					if (header != null && values != null) {
						this.csvRowMap.clear();

						for (int i = 0; i < values.length && i < header.length; ++i) {
							this.csvRowMap.put(header[i], values[i]);
						} // end for

						importRow();
					}
				} // end while
			} finally {
				close(reader);
			}
			if (!isModified()) {
				// No new price data found in %s.
				writeFormatted("FWIMP08", this.importWindow.getFileToImport().getName());
			}
		}

	} // end importFile()

	/**
	 * Import this row of the comma separated value file.
	 */
	private void importRow() throws FwiException {
		Account account = getAccountByInvestNumber(stripQuotes("col.account.num"));
		CurrencyType security = this.securities
			.getCurrencyByTickerSymbol(stripQuotes("col.ticker"));

		if (security == null) {
			verifyAccountBalance(account);
		} else {
			BigDecimal shares = new BigDecimal(stripQuotes("col.shares"));

			storePriceQuoteIfDiff(security, shares);

			verifyShareBalance(account, security.getName(), shares);
		}

	} // end importRow()

	/**
	 * @param security the Moneydance security to use
	 * @param shares the number of shares included in the value
	 */
	private void storePriceQuoteIfDiff(CurrencyType security, BigDecimal shares)
			throws FwiException {
		BigDecimal price = new BigDecimal(stripQuotes("col.price"));
		BigDecimal value = new BigDecimal(stripQuotes("col.value"));

		// see if shares * price = value to 2 places past the decimal point
		if (!shares.multiply(price).setScale(value.scale(), HALF_EVEN).equals(value)) {
			// no, so get the price to the sixth place past the decimal point
			price = value.divide(shares, PRICE_FRACTION_DIGITS, HALF_EVEN);
		}
		NumberFormat priceFmt = getCurrencyFormat(price);
		int importDate = convLocalToDateInt(this.importWindow.getMarketDate());
		CurrencySnapshot latestSnapshot = getLatestSnapshot(security);
		validateCurrentUserRate(security, latestSnapshot, priceFmt);
		double newPrice = price.doubleValue();
		double oldPrice = convRateToPrice(importDate < latestSnapshot.getDateInt()
			? security.getUserRateByDateInt(importDate)
			: latestSnapshot.getUserRate());

		if (importDate != latestSnapshot.getDateInt() || newPrice != oldPrice) {
			// Change %s price from %s to %s (<span class="%s">%+.2f%%</span>).
			String spanCl = newPrice < oldPrice ? CL_DECREASE
				: newPrice > oldPrice ? CL_INCREASE : "";
			writeFormatted("FWIMP03", security.getName(), priceFmt.format(oldPrice),
				priceFmt.format(newPrice), spanCl, (newPrice / oldPrice - 1) * 100);

			new SecurityHandler(security).storeNewPrice(newPrice, importDate);
			++this.numPricesSet;
		}

	} // end storePriceQuoteIfDiff(CurrencyType, BigDecimal)

	/**
	 * @param security
	 * @param latestSnapshot
	 * @param priceFmt
	 * @return the price in latestSnapshot
	 */
	private double validateCurrentUserRate(CurrencyType security,
			CurrencySnapshot latestSnapshot, NumberFormat priceFmt) {
		double price = convRateToPrice(latestSnapshot.getUserRate());
		double oldPrice = convRateToPrice(security.getUserRate());

		if (price != oldPrice) {
			security.setUserRate(latestSnapshot.getUserRate());

			System.err.format(this.locale, "Changed security %s current price from %s to %s.%n",
				security.getName(), priceFmt.format(oldPrice), priceFmt.format(price));
		}

		return price;
	} // end validateCurrentUserRate(CurrencyType, CurrencySnapshot, NumberFormat)

	/**
	 * @param desiredAccountNum
	 * @return the Moneydance investment account with the desired number
	 */
	private Account getAccountByInvestNumber(String desiredAccountNum) {
		Account account = null;
		List<Account> subs = this.root.getSubAccounts(new AcctFilter() {
			public boolean matches(Account acct) {
				String accountNum = acct.getInvestAccountNumber();

				return accountNum.equalsIgnoreCase(desiredAccountNum);
			} // end matches(Account)

			public String format(Account acct) {
				return acct.getFullAccountName();
			} // end format(Account)
		}); // end new AcctFilter() {...}

		if (subs == null || subs.isEmpty()) {
			// Unable to obtain Moneydance investment account with number [%s].
			writeFormatted("FWIMP05", desiredAccountNum);
		} else {
			account = subs.get(0);
		}

		return account;
	} // end getAccountByInvestNumber(String)

	/**
	 * @param account
	 */
	private void verifyAccountBalance(Account account) throws FwiException {
		if (account != null) {
			BigDecimal importedBalance = new BigDecimal(stripQuotes("col.value"));
			double balance = getCurrentBalance(account);

			if (importedBalance.doubleValue() != balance) {
				// Found a different balance in account %s: have %s, imported %s.
				// Note: No Moneydance security for ticker symbol [%s] (%s).
				DecimalFormat df = getCurrencyFormat(importedBalance);
				writeFormatted("FWIMP02", account.getAccountName(), df.format(balance),
					df.format(importedBalance), stripQuotes("col.ticker"),
					stripQuotes("col.name"));
			}
		}

	} // end verifyAccountBalance(Account)

	/**
	 * @param account
	 * @param securityName
	 * @param importedShares
	 */
	private void verifyShareBalance(Account account, String securityName,
			BigDecimal importedShares) {
		if (account != null) {
			Account secAccount = getSubAccountByName(account, securityName);

			if (secAccount != null) {
				double balance = getCurrentBalance(secAccount);

				if (importedShares.doubleValue() != balance) {
					// Found a different %s share balance in account %s: have %s, imported %s.
					DecimalFormat df = getDecimalFormat(importedShares);
					writeFormatted("FWIMP04", secAccount.getAccountName(),
						account.getAccountName(), df.format(balance), df.format(importedShares));
				}
			}
		}

	} // end verifyShareBalance(Account, String, BigDecimal)

	/**
	 * @param account
	 * @return the current account balance
	 */
	private static double getCurrentBalance(Account account) {
		int decimalPlaces = account.getCurrencyType().getDecimalPlaces();

		return account.getUserCurrentBalance() / centMult[decimalPlaces];
	} // end getCurrentBalance(Account)

	/**
	 * @param account the parent account
	 * @param securityName
	 * @return the Moneydance security subaccount with the specified name
	 */
	private Account getSubAccountByName(Account account, String securityName) {
		Account subAccount = null;
		List<Account> subs = account.getSubAccounts(new AcctFilter() {
			public boolean matches(Account acct) {
				String accountName = acct.getAccountName();

				return accountName.equalsIgnoreCase(securityName);
			} // end matches(Account)

			public String format(Account acct) {
				return acct.getFullAccountName();
			} // end format(Account)
		}); // end new AcctFilter() {...}

		if (subs == null || subs.isEmpty()) {
			// Unable to obtain Moneydance security [%s] in account %s.
			writeFormatted("FWIMP06", securityName, account.getAccountName());
		} else {
			subAccount = subs.get(0);
		}

		return subAccount;
	} // end getSubAccountByName(Account, String)

	/**
	 * @param propKey property key for column header
	 * @return value from the csv row map with any surrounding double quotes removed
	 */
	private String stripQuotes(String propKey) throws FwiException {
		String csvColumnKey = getFwImportProps().getProperty(propKey);
		String val = this.csvRowMap.get(csvColumnKey);
		if (val == null) {
			// Unable to locate column %s (%s) in %s. Found columns %s
			throw new FwiException(null, "FWIMP11", csvColumnKey, propKey,
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
			writeFormatted("FWIMP12", this.importWindow.getFileToImport(), e);
		}

		return reader;
	} // end openFile()

	/**
	 * @param reader
	 * @return true when the next read will not block for input, false otherwise
	 */
	private boolean hasMore(BufferedReader reader) throws FwiException {
		try {

			return reader.ready();
		} catch (Exception e) {
			// Exception checking file %s.
			throw new FwiException(e, "FWIMP13", this.importWindow.getFileToImport());
		}
	} // end hasMore(BufferedReader)

	/**
	 * @param reader
	 * @return the comma separated tokens from the next line in the file
	 */
	private String[] readLine(BufferedReader reader) throws FwiException {
		try {
			String line = reader.readLine();

			return line == null ? null : line.split(",");
		} catch (Exception e) {
			// Exception reading from file %s.
			throw new FwiException(e, "FWIMP14", this.importWindow.getFileToImport());
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
	 * Commit any changes to Moneydance.
	 */
	public void commitChanges() {
		for (SecurityHandler sHandler : this.priceChanges) {
			sHandler.applyUpdate();
		}
		// Changed %d security price%s.
		writeFormatted("FWIMP07", this.numPricesSet, this.numPricesSet == 1 ? "" : "s");

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
	 * @return true when the we have uncommitted changes in memory
	 */
	public boolean isModified() {

		return !this.priceChanges.isEmpty();
	} // end isModified()

	/**
	 * Release any resources we acquired.
	 *
	 * @return null
	 */
	public FwImporter releaseResources() {
		// nothing to release

		return null;
	} // end releaseResources()

	/**
	 * @return our properties
	 */
	private Properties getFwImportProps() throws FwiException {
		if (this.fwImportProps == null) {
			InputStream propsStream = getClass().getClassLoader()
				.getResourceAsStream(propertiesFileName);
			if (propsStream == null)
				// Unable to find %s on the class path.
				throw new FwiException(null, "FWIMP15", propertiesFileName);

			this.fwImportProps = new Properties();
			try {
				this.fwImportProps.load(propsStream);
			} catch (Exception e) {
				this.fwImportProps = null;

				// Exception loading %s.
				throw new FwiException(e, "FWIMP16", propertiesFileName);
			} finally {
				try {
					propsStream.close();
				} catch (Exception e) { /* ignore */ }
			}
		}

		return this.fwImportProps;
	} // end getFwImportProps()

	/**
	 * @return our message bundle
	 */
	private static ResourceBundle getMsgBundle() {
		if (msgBundle == null) {
			try {
				msgBundle = ResourceBundle.getBundle(baseMessageBundleName);
			} catch (Exception e) {
				System.err.format("Unable to load message bundle %s. %s%n", baseMessageBundleName, e);
				msgBundle = new ResourceBundle() {
					protected Object handleGetObject(String key) {
						// just use the key since we have no message bundle
						return key;
					}

					public Enumeration<String> getKeys() {
						return null;
					}
				}; // end new ResourceBundle() {...}
			} // end catch
		}

		return msgBundle;
	} // end getMsgBundle()

	/**
	 * Inner class to house exceptions.
	 */
	public static class FwiException extends Exception {

		private static final long serialVersionUID = -2928482709902784770L;

		/**
		 * @param cause Exception that caused this (null if none)
		 * @param key The resource bundle key (or message)
		 * @param params Optional parameters for the detail message
		 */
		public FwiException(Throwable cause, String key, Object... params) {
			super(String.format(retrieveMessage(key), params), cause);

		} // end (Throwable, String, Object...) constructor

	} // end class FwiException

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
	 * @param security
	 * @return the last currency snap shot for the supplied security
	 */
	private static CurrencySnapshot getLatestSnapshot(CurrencyType security) {
		List<CurrencySnapshot> snapShots = security.getSnapshots();

		return snapShots.get(snapShots.size() - 1);
	} // end getLatestSnapshot(CurrencyType)

	/**
	 * @param rate the Moneydance currency rate for a security
	 * @return the security price rounded to the tenth place past the decimal point
	 */
	private static double convRateToPrice(double rate) {

		return roundPrice(1 / rate);
	} // end convRateToPrice(double)

	/**
	 * @param price
	 * @return price rounded to the tenth place past the decimal point
	 */
	private static double roundPrice(double price) {
		BigDecimal bd = BigDecimal.valueOf(price);

		return bd.setScale(10, HALF_EVEN).doubleValue();
	} // end roundPrice(double)

	/**
	 * @param amount
	 * @return a currency decimal format with the number of fraction digits in amount
	 */
	private DecimalFormat getCurrencyFormat(BigDecimal amount) {
		DecimalFormat formatter = (DecimalFormat) NumberFormat.getCurrencyInstance(this.locale);
		formatter.setMinimumFractionDigits(amount.scale());

		return formatter;
	} // end getCurrencyFormat(BigDecimal)

	/**
	 * @param val
	 * @return a decimal format with the number of fraction digits in val
	 */
	private DecimalFormat getDecimalFormat(BigDecimal val) {
		DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(this.locale);
		formatter.setMinimumFractionDigits(val.scale());

		return formatter;
	} // end getDecimalFormat(BigDecimal)

	/**
	 * @param date the local date value
	 * @return the corresponding numeric date value in decimal form YYYYMMDD
	 */
	private static int convLocalToDateInt(LocalDate date) {
		int dateInt = date.getYear() * 10000
				+ date.getMonthValue() * 100
				+ date.getDayOfMonth();

		return dateInt;
	} // end convLocalToDateInt(LocalDate)

} // end class FwImporter
