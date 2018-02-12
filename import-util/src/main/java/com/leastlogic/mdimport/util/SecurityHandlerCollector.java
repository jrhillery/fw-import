/*
 * Created on Jan 20, 2018
 */
package com.leastlogic.mdimport.util;

/**
 * This interface provides a way to add a security handler to a collection.
 */
public interface SecurityHandlerCollector {

	/**
	 * Add a security handler to our collection.
	 *
	 * @param handler
	 */
	void addHandler(SecurityHandler handler);

} // end interface SecurityHandlerCollector
