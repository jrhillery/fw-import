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
	private SecurityHandlerCollector handlerCollector;

	private double newPrice = 0;
	private int newDate = 0;
	private long newVolume = 0;
	private double newHighPrice = 0;
	private double newLowPrice = 0;

	/**
	 * Sole constructor.
	 *
	 * @param security
	 * @param handlerCollector
	 */
	public SecurityHandler(CurrencyType security,
			SecurityHandlerCollector handlerCollector) {
		this.security = security;
		this.handlerCollector = handlerCollector;

	} // end (CurrencyType, SecurityHandlerCollector) constructor

	/**
	 * Store a deferred price quote for a specified date integer.
	 *
	 * @param newPrice price quote
	 * @param newDate date integer
	 */
	public void storeNewPrice(double newPrice, int newDate) {
		this.newPrice = newPrice;
		this.newDate = newDate;
		this.handlerCollector.addHandler(this);

	} // end storeNewPrice(double, int)

	/**
	 * Store a deferred price quote with volume, high and low prices too.
	 *
	 * @param newPrice price quote
	 * @param newDate date integer
	 * @param newVolume daily volume
	 * @param newHighPrice daily high price
	 * @param newLowPrice daily low price
	 */
	public void storeNewPrice(double newPrice, int newDate, long newVolume,
			double newHighPrice, double newLowPrice) {
		this.newPrice = newPrice;
		this.newDate = newDate;
		this.newVolume = newVolume;
		this.newHighPrice = newHighPrice;
		this.newLowPrice = newLowPrice;
		this.handlerCollector.addHandler(this);

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

		if (this.newDate >= latestSnapshot.getDateInt()) {
			this.security.setUserRate(1 / this.newPrice);
		}

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
