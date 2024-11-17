/*
 * Created on Jan 17, 2018
 */
package com.moneydance.modules.features.yqimport;

import com.leastlogic.moneydance.util.MdLog;
import com.moneydance.apps.md.controller.FeatureModule;

/**
 * Module used to import Yahoo quote data into Moneydance.
 */
public class Main extends FeatureModule implements AutoCloseable {
	private YqImportWindow importWindow = null;
	private YqImporter importer = null;

	/**
	 * Register this module to be invoked via the Extensions menu.
	 *
	 * @see com.moneydance.apps.md.controller.FeatureModule#init()
	 */
	public void init() {
		getContext().registerFeature(this, "do:yq:import", null, getName());
		MdLog.setPrefix("YQIMPT: ");

	} // end init()

	/**
	 * This is called when this extension is invoked.
	 *
	 * @see com.moneydance.apps.md.controller.FeatureModule#invoke(java.lang.String)
	 */
	public void invoke(String uri) {
		MdLog.all("%s invoked with uri [%s]".formatted(getName(), uri));
		showWindow();

		this.importer = new YqImporter(this.importWindow, getContext().getCurrentAccountBook());

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

	private void handleException(Throwable e) {
		MdLog.all("Problem invoking %s".formatted(getName()), e);
		this.importWindow.addText(e.toString());
		this.importWindow.enableCommitButton(false);

	} // end handleException(Throwable)

	/**
	 * Stop execution, close our console window and release resources.
	 */
	public synchronized void cleanup() {
		if (this.importWindow != null)
			this.importWindow = this.importWindow.goAway();

	} // end cleanup()

	public String getName() {

		return "YQ Import";
	} // end getName()

	/**
	 * Show our console window.
	 */
	private synchronized void showWindow() {
		if (this.importWindow == null) {
			this.importWindow = new YqImportWindow(this,
				getContext().getCurrentAccountBook().getLocalStorage());
			this.importWindow.addCloseableResource(this);
			this.importWindow.setVisible(true);
		} else {
			this.importWindow.setVisible(true);
			this.importWindow.toFront();
			this.importWindow.requestFocus();
		}

	} // end showWindow()

	/**
	 * Closes this resource, relinquishing any underlying resources.
	 */
	public void close() {
		this.importWindow = null;
		this.importer = null;

	} // end close()

} // end class Main
