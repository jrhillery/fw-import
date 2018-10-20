/*
 * Created on Jan 20, 2018
 */
package com.leastlogic.mdimport.util;

import com.infinitekind.moneydance.model.CurrencySnapshot;
import com.infinitekind.moneydance.model.CurrencyType;
import com.leastlogic.moneydance.util.MdUtil;

/**
 * This object handles deferred updates to a Moneydance security.
 */
public class SecurityHandler {
	private CurrencyType security;

	private double newPrice = 0;
	private int newDate = 0;
	private long newVolume = 0;
	private double newHighPrice = 0;
	private double newLowPrice = 0;

	/**
	 * Sole constructor.
	 *
	 * @param security The Moneydance security to handle
	 */
	public SecurityHandler(CurrencyType security) {
		this.security = security;

	} // end (CurrencyType) constructor

	/**
	 * Store a deferred price quote for a specified date integer.
	 *
	 * @param newPrice Price quote
	 * @param newDate Date integer
	 * @return This instance
	 */
	public SecurityHandler storeNewPrice(double newPrice, int newDate) {
		this.newPrice = newPrice;
		this.newDate = newDate;

		return this;
	} // end storeNewPrice(double, int)

	/**
	 * Store a deferred price quote with volume, high and low prices too.
	 *
	 * @param newPrice Price quote
	 * @param newDate Date integer
	 * @param newVolume Daily volume
	 * @param newHighPrice Daily high price
	 * @param newLowPrice Daily low price
	 */
	public void storeNewPrice(double newPrice, int newDate, long newVolume,
			double newHighPrice, double newLowPrice) {
		this.newPrice = newPrice;
		this.newDate = newDate;
		this.newVolume = newVolume;
		this.newHighPrice = newHighPrice;
		this.newLowPrice = newLowPrice;

	} // end storeNewPrice(double, int, long, double, double)

	/**
	 * Apply the stored update.
	 */
	public void applyUpdate() {
		CurrencySnapshot latestSnapshot = MdUtil.getLatestSnapshot(this.security);
		CurrencySnapshot newSnapshot = this.security.setSnapshotInt(this.newDate,
			1 / this.newPrice);

		if (this.newHighPrice > 0 && this.newLowPrice > 0) {
			newSnapshot.setDailyVolume(this.newVolume);
			newSnapshot.setUserDailyHigh(1 / this.newHighPrice);
			newSnapshot.setUserDailyLow(1 / this.newLowPrice);
		}

		if (latestSnapshot == null || this.newDate >= latestSnapshot.getDateInt()) {
			this.security.setUserRate(1 / this.newPrice);
		}
		this.security.syncItem();

	} // end applyUpdate()

	/**
	 * @return This handler's security
	 */
	public CurrencyType getSecurity() {

		return this.security;
	} // end getSecurity()

	/**
	 * @return A string representation of this SecurityHandler
	 */
	public String toString() {

		return this.security.getTickerSymbol() + ":" + this.newPrice;
	} // end toString()

} // end class SecurityHandler
