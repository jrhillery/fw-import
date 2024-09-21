/*
 * Created on Nov 10, 2017
 */
package com.moneydance.modules.features.fwimport;

import com.moneydance.apps.md.controller.FeatureModule;

/**
 * Module used to import Fidelity NetBenefits workplace account data into Moneydance.
 */
public class Main extends FeatureModule {
	private FwImportWindow importWindow = null;
	private FwImporter importer = null;

	/**
	 * Register this module to be invoked via the extensions menu.
	 *
	 * @see com.moneydance.apps.md.controller.FeatureModule#init()
	 */
	public void init() {
		getContext().registerFeature(this, "do:fw:import", null, getName());

	} // end init()

	/**
	 * This is called when this extension is invoked.
	 *
	 * @see com.moneydance.apps.md.controller.FeatureModule#invoke(java.lang.String)
	 */
	public void invoke(String uri) {
		System.err.println(getName() + " invoked with uri [" + uri + ']');
		showWindow();

		this.importer = new FwImporter(this.importWindow, getContext().getCurrentAccountBook());

	} // end invoke(String)

	/**
	 * Import the selected file using the specified market date.
	 */
	void importFile() {
		try {
			synchronized (this) {
				this.importWindow.clearText();
				this.importer.forgetChanges();
				this.importer.importFile();
			}
			this.importWindow.enableCommitButton(this.importer.isModified());
		} catch (Throwable e) {
			handleException(e);
		}

	} // end importFile()

	/**
	 * This is called when the commit button is selected.
	 */
	void commitChanges() {
		try {
			synchronized (this) {
				this.importer.commitChanges();
			}
			this.importWindow.enableCommitButton(this.importer.isModified());
		} catch (Throwable e) {
			handleException(e);
		}

	} // end commitChanges()

	private void handleException(Throwable e) {
		this.importWindow.addText(e.toString());
		this.importWindow.enableCommitButton(false);
		e.printStackTrace(System.err);

	} // end handleException(Throwable)

	public void cleanup() {
		closeWindow();

	} // end cleanup()

	public String getName() {

		return "FW Import";
	} // end getName()

	/**
	 * Show our window.
	 */
	private synchronized void showWindow() {
		if (this.importWindow == null) {
			this.importWindow = new FwImportWindow(this);
			this.importWindow.setVisible(true);
		} else {
			this.importWindow.setVisible(true);
			this.importWindow.toFront();
			this.importWindow.requestFocus();
		}

	} // end showWindow()

	/**
	 * Close our window and release resources.
	 */
	synchronized void closeWindow() {
		if (this.importWindow != null)
			this.importWindow = this.importWindow.goAway();

		if (this.importer != null)
			this.importer = this.importer.releaseResources();

	} // end closeWindow()

} // end class Main
