package pco.schneider.api;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.sciforma.psnext.api.DataViewRow;
import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.Resource;
import com.sciforma.psnext.api.Session;
import com.sciforma.psnext.api.User;

import pco.schneider.main.Constants;

public class WizardManager {

	private String actionName;

	private static final Logger LOG = Logger.getLogger(SciformaManager.class);
	
	private static final CustomLog CUSTOM_LOG = CustomLog.getInstance();

	private Map<String, User> usersMap = new HashMap<String, User>();

	private Map<String, Resource> resourcesMap = new HashMap<String, Resource>();
	
	private Map<String, DataViewRow> dataviewsMap = new HashMap<String, DataViewRow>();

	private List<String> allResTransTable = new ArrayList<String>();

	private HashMap<String, Resource> rowsToRemove = new HashMap<String, Resource>();

	private SciformaManager sciforma;

	private Properties props;

	private String confDir;

	public WizardManager(Properties props) {
		this.props = props;
	}

	public int execute(int action, String actionName, String confDir) {
		this.actionName = actionName;
		this.confDir = confDir;
		
		int init = init();
		
		if (init != 0) {
			return init;
		}
		
		return process(action);
	}
	
	public int init() {
		int result = 0;
		LOG.debug("*** Loading Sciforma data in init phase ***");
		this.sciforma = new SciformaManager(this.props);
		Session session = this.sciforma.getSession(this.confDir);
		
		if (session == null || !session.isLoggedIn()) {
			return 4;
		}

		if (!this.sciforma.setShowInactiveResources()) {
			return 5;
		}
		
		this.usersMap = this.sciforma.getUsersMap();

		if (this.usersMap.size() == 0) {
			return 6;
		}

		this.resourcesMap = this.sciforma.getResourcesMap();

		if (this.resourcesMap.size() == 0) {
			return 8;
		}

		return result;
	}

	public int process(int action) {
		int result = 0;
		this.sciforma.loadInactiveResourcesFromNowOn();

		switch (action) {

			case Constants.ACTION_TRANSFER:
	
				try {
					LOG.info("*** Transfer starts ***");
					this.processTransfer();
					LOG.info("*** Transfer done ***");
				} catch (Exception e) {
					result = 9;
					LOG.error("Action Transfer Fail", e);
					CUSTOM_LOG.error("Action Transfer Fail" + e);
				}
	
				break;
	
			case Constants.ACTION_LDAP:
	
				try {
					LOG.info("*** LDAP starts ***");
					this.processLDAP();
					LOG.info("*** LDAP done ***");
				} catch (Exception e) {
					result = 10;
					LOG.error("Action LDAP process fail", e);
					CUSTOM_LOG.error("Action LDAP process fail" + e);
				}
	
				break;
	
			default:
				LOG.info("No valid action detected, DO NOTHING.");
				LOG.info("Valid actions are transfer and ldap.");
				result = 11;
				break;
				
		}
		
		result = close();
		return result;
	}

	private int close() {
		int result = 0;
		final Calendar cal = Calendar.getInstance();

		String traceFileName = ("mstt-rm-wizard-" + this.actionName + "-log_" + cal.get(Calendar.YEAR) + "-"
				+ String.format("%02d", (cal.get(Calendar.MONTH) + 1)) + "-"
				+ String.format("%02d", cal.get(Calendar.DATE)) + "_"
				+ String.format("%02d", cal.get(Calendar.HOUR_OF_DAY)) + "-"
				+ String.format("%02d", cal.get(Calendar.MINUTE)) + "-"
				+ String.format("%02d", cal.get(Calendar.SECOND)) + ".txt");
		
		final String fileNameToSave = this.props.getProperty("data.path") + File.separator + traceFileName;

		try {
			final FileWriter logfile = new FileWriter(fileNameToSave);

			for (int i = 0; i < CUSTOM_LOG.getTraces().size(); i++) {
				logfile.write((String) CUSTOM_LOG.getTraces().get(i) + "\n");
				LOG.debug("Writing log " + (String) CUSTOM_LOG.getTraces().get(i));
			}

			logfile.close();
		}

		catch (IOException e) {
			LOG.warn("could not write log file", e);
			result = 12;
		}

		LOG.debug("end close()");
		return result;
	}

	private boolean processTransfer() {
		List<Resource> oldResourceList = new ArrayList<Resource>();
		List<Resource> transferResourceList = new ArrayList<Resource>();
		List<String> successSesaList = new ArrayList<String>();
		List<Resource> allDvResources = new ArrayList<Resource>();
		
		this.allResTransTable = this.sciforma.getResourceTransferTable();

		if (this.allResTransTable.size() == 0) {
			return false;
		}
		
		LOG.debug("Running on all the resource (" + this.resourcesMap.size() + " found)");
		
		for (String resTransTable : this.allResTransTable) {
			Resource resource = this.resourcesMap.get(resTransTable);
			allDvResources.add(resource);

			if (resTransTable.equals(Constants.TRANSFER + resTransTable)) {
				LOG.debug("Adding " + resource + " to the 'transfer resource list' ");
				transferResourceList.add(resource);
				LOG.debug("TRANSFER : " + resTransTable);
			} else if (resTransTable.contains("_" + resTransTable) || resTransTable.contains(resTransTable)) {
				LOG.debug(resTransTable + " contains " + resTransTable + " - " + resTransTable.contains("_" + resTransTable) + ".");
				LOG.debug("Adding " + resource + " to the 'old resource list' ");
				oldResourceList.add(resource);
				LOG.debug("OLD : " + resTransTable);
			} else {
				LOG.debug("None of the conditions were fulfilled.");
			}

		}

		LOG.debug("Nb resources found = " + oldResourceList.size());
		
		LOG.debug("All DV Resources = " + allDvResources.size());

		// Make a list of DataViewRow corresponding with list of resource
		LOG.debug("Looping on oldResourceList elements");
		
		for (Resource res : oldResourceList) {
			LOG.debug("Resource " + res + " found in old elems. Reading data view " + Constants.RESOURCE_TRANSFER
					+ " to store the rows in memory.");
			this.dataviewsMap = this.sciforma.getDataViewRowMap(Constants.RESOURCE_TRANSFER, res);
		}

		try {
			LOG.debug("Looping on the 'transferResourceList' elements");

			for (Resource res : transferResourceList) {
				LOG.debug("Processing resource " + res + " that needs transfer. Running on all its elements.");
				String resaSesa = res.getStringField(Constants.SESA_CODE);
				DataViewRow row = this.dataviewsMap.get(resaSesa);
				
				if (row != null) {	
					String rowSesa = row.getStringField(Constants.SESA_CODE);
					String workflow = row.getStringField(Constants.WORKFLOW_STATE);
					LOG.debug("Condition check: res.getStringField: " + resaSesa + ", "
							+ "TRANSFER+CODE = " + Constants.TRANSFER + rowSesa + ", " + "Workflow state: "
							+ workflow);
					LOG.debug("Checking the 'Approved' case. " + "SESA_CODE is " + resaSesa + ", row's SESA_CODE " + rowSesa);

					if (resaSesa.equals(Constants.TRANSFER + rowSesa) && workflow.equals("Approved")) {
						this.sciforma.updateApprovedResource(res, resaSesa, row, rowSesa, oldResourceList, this.rowsToRemove, successSesaList, allDvResources);

					} else if (resaSesa.equals(Constants.TRANSFER + rowSesa) && workflow.equalsIgnoreCase("Rejected")) {
						this.sciforma.updateRejectedResource(res, resaSesa, row, rowSesa, oldResourceList, this.rowsToRemove);
					}
					
				}
				
			}

			// suppress all data view rows for successful resource
			LOG.debug("Running through the records flagged as 'old resource list' to remove records that need removing.");

			for (Resource res : oldResourceList) {
				String sesaCode = res.getStringField(Constants.SESA_CODE);
				LOG.debug("Looping through res " + res + ", looking to remove sesaCode: "
						+ sesaCode.substring(sesaCode.indexOf("_") + 1));

				if (successSesaList.contains(sesaCode.substring(sesaCode.indexOf("_") + 1))) {
					LOG.debug("Record found - removing it");
					this.sciforma.removeDataViewRowList(Constants.RESOURCE_TRANSFER, res, this.rowsToRemove);
				}

			}
			
			// suppress all data view rows for rejected resource
			LOG.debug("Running through all other dv resources to check if any must be removed.");

			for (Resource res : allDvResources) {
				LOG.debug("Running on rows from res " + res + ".");
				this.sciforma.removeDataViewRowList(Constants.RESOURCE_TRANSFER, res, false, false, this.rowsToRemove);
			}

		} catch (PSException e) {
			LOG.error(e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			LOG.error(e.getMessage());
			e.printStackTrace();
		}

		return true;
	}

	private void processLDAP() {
		this.sciforma.updateResources(Constants.IDS_MODIFIED);
	}

}
