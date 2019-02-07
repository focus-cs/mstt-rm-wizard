package pco.schneider.api;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.sciforma.psnext.api.AccessException;
import com.sciforma.psnext.api.DataFormatException;
import com.sciforma.psnext.api.DataViewRow;
import com.sciforma.psnext.api.DatedData;
import com.sciforma.psnext.api.DoubleDatedData;
import com.sciforma.psnext.api.Global;
import com.sciforma.psnext.api.InternalFailure;
import com.sciforma.psnext.api.LockException;
import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.Resource;
import com.sciforma.psnext.api.Session;
import com.sciforma.psnext.api.User;

import pco.schneider.main.Constants;

public class SciformaManager {

	private static final Logger LOG = Logger.getLogger(SciformaManager.class);

	private static final CustomLog CUSTOM_LOG = CustomLog.getInstance();

	private Session session;

	private Properties props;

	private Global global;

	private Map<String, Resource> resourcesMap = new HashMap<String, Resource>();

	private Map<String, User> usersMap = new HashMap<String, User>();
	
	private Map<String, DataViewRow> dataviewsMap;

	public SciformaManager(Properties props) {
		this.props = props;
	}

	public Session getSession(String confDir) {
		String url = this.props.getProperty("psnext.url");
		String login = this.props.getProperty("psnext.login");
		String password = this.props.getProperty("psnext.password");

		if (this.session == null) {

			try {
				LOG.debug("*** Connecting to " + url + " ***");
				this.session = new Session(url);
				this.session.login(login, password.toCharArray());
				this.global = new Global();
				LOG.debug("*** Connected to PSNext. ***");
			} catch (PSException e) {
				LOG.error(e);
				e.printStackTrace();
			}

		}

		PropertyConfigurator.configure(confDir + File.separator + Constants.LOG_FILE);
		return this.session;
	}

	@SuppressWarnings("unchecked")
	public List<String> getResourceTransferTable() {
		List<String> allResTransTable = new ArrayList<String>();
		LOG.debug("*** Retrieving the list of resources to transfer from the field 'resourceTransferList'. ***");
		
		try {
			allResTransTable = (List<String>) this.global.getListField("resourceTransferList");
		} catch (DataFormatException e) {
			LOG.error(e);
			e.printStackTrace();
		} catch (PSException e) {
			LOG.error(e);
			e.printStackTrace();
		}

		LOG.debug("*** Found " + allResTransTable.size() + " elems. ***");
		LOG.debug("** Found ID : " + java.util.Arrays.toString(allResTransTable.toArray()) + " ***");
		
		if (allResTransTable.size() == 0) {
			LOG.info("*** No resources have been found to transfer at approval state. ***");
		}

		return allResTransTable;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Resource> getResourcesMap() {
		List<Resource> resourceList = null;
		LOG.debug("*** Retrieving all resources from Sciforma. ***");
		
		try {
			resourceList = this.session.getResourceList();

			if (resourceList != null) {
				LOG.debug("Mapping resources based on their ID.");

				for (Resource resource : resourceList) {
					LOG.debug("Adding resource " + resource + " to the list of resources to process ('resourcesMap').");
					this.resourcesMap.put(resource.getStringField(Constants.SESA_CODE), resource);
				}

			}

		} catch (PSException e) {
			LOG.error(e);
			e.printStackTrace();
		}

		return this.resourcesMap;
	}

	@SuppressWarnings("unchecked")
	public Map<String, User> getUsersMap() {
		List<User> usersList = null;
		LOG.debug("*** Retrieving the user list. ***");
		
		try {
			usersList = this.session.getUserList();

			if (usersList != null) {
				LOG.debug("Mapping users based on their ID.");

				for (User user : usersList) {
					LOG.debug("Adding user " + user + " to the list of users to process ('usersMap').");
					this.usersMap.put(user.getStringField(Constants.SESA_CODE), user);
				}

			}

		} catch (PSException e) {
			LOG.error(e);
			e.printStackTrace();
		}

		return this.usersMap;
	}

	@SuppressWarnings("unchecked")
	public Map<String, DataViewRow> getDataViewsMap(String tableName) {
		LOG.debug("*** Retrieving records from dataview " + tableName + " to update the resource list. ***");
		List<DataViewRow> dataViewRows = null;

		try {
			dataViewRows = this.session.getDataViewRowList(tableName,this.global);

		} catch (PSException e) {
			LOG.error("Unexpected error while loading " + tableName, e);
			CUSTOM_LOG.error("Unexpected error while loading " + tableName + e);
			e.printStackTrace();
		}
		
		if (dataViewRows.size() == 0) {
			LOG.info("No resources have been found to update.");
		}
	
		this.dataviewsMap = new HashMap<String, DataViewRow>();
		String sesaCode = "";
		LOG.debug("Mapping all records from the dataview " + tableName + ", using the SESA_CODE as the key.");
	
		for (int i = 0; i < dataViewRows.size(); i++) {
	
			try {
				sesaCode = dataViewRows.get(i).getStringField(Constants.SESA_CODE);
	
				if (this.dataviewsMap.get(sesaCode) != null) {
					LOG.warn("Previous record for SESA_CODE " + sesaCode + " overwritten, because "
							+ "many data view rows were found for this SESA_CODE.");
				}
	
				this.dataviewsMap.put(sesaCode, dataViewRows.get(i));
			} catch (PSException e) {
				LOG.error(e.getMessage());
			}
	
		}
	
		return this.dataviewsMap;
	}

	public boolean setShowInactiveResources() {
		boolean result = false;
		LOG.debug("*** Setting Show inactive resources WS to true. ***");
		
		try {
			this.global.lock();
			this.global.setBooleanField("Show inactive resources WS", true);
			this.global.save(false);
		} catch (DataFormatException e) {
			LOG.error(e);
			e.printStackTrace();
		} catch (PSException e) {
			LOG.error(e);
			e.printStackTrace();
		} finally {
			
			if (this.global != null) {
				
				try {
					this.global.unlock();
					result = true;
				} catch (PSException e) {
					LOG.error(e);
					e.printStackTrace();
				}
				
			}
			
		}

		return result;
	}

//	public boolean openGlobal() {
//		boolean result = false;
//		LOG.debug("*** Locking global ***");
//		
//		if (this.global == null) {
//			this.global = new Global();
//		}
//
//		try {
//			this.global.lock();
//			result = true;
//			LOG.debug("*** Global locked ***");
//		} catch (LockException e) {
//			LOG.error("*** Global locked by: " + e.getLockingUser() + " ***");
//			LOG.error("*** Unable to lock Global" + e.toString() + " ***");
//			e.printStackTrace();
//		} catch (PSException e) {
//			LOG.error("*** Unable to lock Global ***" + e.toString() + " ***");
//			e.printStackTrace();
//		}
//
//		return result;
//	}
//
//	public boolean closeGlobal() {
//		boolean result = false;
//		LOG.debug("*** Unlocking global ***");
//		
//		try {
//			this.global.save(false);
//		} catch (PSException e) {
//			LOG.error(e);
//			e.printStackTrace();
//		}
//
//		try {
//			this.global.unlock();
//			result = true;
//			LOG.debug("*** Global unlocked ***");
//		} catch (PSException e) {
//			LOG.error(e);
//			e.printStackTrace();
//		}
//
//		return result;
//	}
	
	public void loadInactiveResourcesFromNowOn() {
		LOG.debug("*** Entering process function ***");
		this.session.loadInactiveResourcesFromNowOn();
		LOG.debug("** Loading inactive resources: " + this.session.loadingInactiveResources() + " **");
	}
	
	public boolean updateResources(String tableName) {
		LOG.debug("*** Running on all the records from the map of records (looping on each of the keys). ***");
		this.dataviewsMap = getDataViewsMap(tableName);
		
		for (String id : this.dataviewsMap.keySet()) {

			if (this.resourcesMap.containsKey(id)) {
				LOG.debug("Updating resource " + id + ", because it was found in Sciforma's resource list.");
				this.updateResource(id, this.resourcesMap.get(id), this.dataviewsMap.get(id));
				this.updateResource("TRANSFER_" + id, this.resourcesMap.get("TRANSFER_" + id), this.dataviewsMap.get(id));
			} else {
				LOG.warn("Sesa code " + id + " is not an existing resource in MSTT.");
				CUSTOM_LOG.warn("1;" + id + ";11");
			}

		}

		return true;
	}

	private void updateResource(String resId, Resource res, DataViewRow row) {

		if (res == null) {
			CUSTOM_LOG.error("1;" + resId + ";14");
			return;
		}

		try {
			LOG.debug("Locking resource " + resId + " for modifications.");
			res.lock();
			this.updateStringField(res, Constants.BRIDGE_ID, row.getStringField(Constants.BRIDGE_ID));
			this.updateStringField(res, Constants.LAST_NAME, row.getStringField(Constants.LAST_NAME));
			this.updateStringField(res, Constants.FIRST_NAME, row.getStringField(Constants.FIRST_NAME));
			this.updateStringField(res, Constants.JOB_CODE, row.getStringField(Constants.JOB_CODE));
			this.updateStringField(res, Constants.RES_EMAIL_ADDRESS, row.getStringField(Constants.VIEW_EMAIL_ADDRESS));
			LOG.debug("Saving resource " + resId + ".");
			res.save(false);
			LOG.info("Resource with sesa code " + resId + " has been updated.");
		} catch (LockException e) {
			LOG.info("Resource with sesa code " + resId + " is locked and cannot be updated.");
			CUSTOM_LOG.error("1;" + res + ";12");
			e.printStackTrace();
		} catch (PSException e) {
			CUSTOM_LOG.error("1;" + res + ";13");
			LOG.info("Resource with sesa code " + resId + " cannot be saved.");
			CUSTOM_LOG.warn("Resource with sesa code " + resId + " cannot be saved.");
			e.printStackTrace();
		} finally {
			LOG.debug("Unlocking resource " + resId + ".");
			
			try {
				res.unlock();
			} catch (InternalFailure e) {
				CUSTOM_LOG.error("1;" + resId + ";13");
				LOG.info("Resource with sesa code " + resId + " cannot be unlocked.");
				CUSTOM_LOG.warn("Resource with sesa code " + resId + " cannot be unlocked.");
				e.printStackTrace();
			} catch (AccessException e) {
				CUSTOM_LOG.error("1;" + resId + ";13");
				LOG.info("Resource with sesa code " + resId + " cannot be unlocked.");
				CUSTOM_LOG.warn("Resource with sesa code " + resId + " cannot be unlocked.");
				e.printStackTrace();
			}
			
		}

		LOG.debug("Processing user data corresponding to the resource id " + resId + ".");
		User user = null;

		try {
			user = this.usersMap.get(resId);
			String id = user.getStringField(Constants.SESA_CODE);
			LOG.debug("Found user " + id + ". Trying to lock the object.");
			user.lock();
			LOG.debug("Updating the field 'SSO' with value " + row.getBooleanField(Constants.VIEW_SSO) + ".");
			user.setBooleanField(Constants.SSO, row.getBooleanField(Constants.VIEW_SSO));
			user.save(false);
			LOG.info("user with sesa code " + resId + " has been updated with SSO value " + row.getBooleanField(Constants.VIEW_SSO) + ".");
		} catch (LockException e) {
			LOG.info("User with sesa code " + resId + " is locked and cannot be updated.");
			CUSTOM_LOG.error("1;" + resId + ";12");
			e.printStackTrace();
		} catch (PSException e) {
			CUSTOM_LOG.error("1;" + resId + ";13");
			LOG.error("User with sesa code " + resId + " cannot be saved.");
			CUSTOM_LOG.error("User with sesa code " + resId + " cannot be saved.");
			e.printStackTrace();
		} finally {
			LOG.debug("Unlocking user " + resId + ".");
			
			try {
				user.unlock();
			} catch (InternalFailure e) {
				CUSTOM_LOG.warn("User with sesa code " + resId + " cannot be unlocked.");
				LOG.error("User with sesa code " + resId + " cannot be unlocked.");
				LOG.error(e);
				e.printStackTrace();
			} catch (AccessException e) {
				CUSTOM_LOG.warn("User with sesa code " + resId + " cannot be unlocked.");
				LOG.error("User with sesa code " + resId + " cannot be unlocked.");
				LOG.error(e);
				e.printStackTrace();
			}
			
		}

	}

	private void updateStringField(Resource res, String fieldName, String value)
			throws DataFormatException, PSException {
		LOG.debug("For resource " + res + ", updating field " + fieldName + " with data " + value + ".");
		res.setStringField(fieldName, value);
	}

	@SuppressWarnings("unchecked")
	public Map<String, DataViewRow> getDataViewRowMap(String dataViewName, Resource resource) {
		Map<String, DataViewRow> dataviewsMap = new HashMap<String, DataViewRow>();
		List<DataViewRow> dataViewRows = null;

		try {
			dataViewRows = this.session.getDataViewRowList(dataViewName, resource);
		} catch (PSException e) {
			LOG.error("Unexpected error while loading " + dataViewName, e);
			CUSTOM_LOG.error("Unexpected error while loading " + dataViewName + e);
			e.printStackTrace();
		}

		if (dataViewRows != null && !dataViewRows.isEmpty()) {
			
			for (DataViewRow dataViewRow : dataViewRows) {

				try {
					String resSesa = dataViewRow.getStringField(Constants.SESA_CODE);
					LOG.debug("Adding row with SESA_CODE " + resSesa
							+ "and workflow state: " + dataViewRow.getStringField(Constants.WORKFLOW_STATE)
							+ " for resource " + resource);
							dataviewsMap.put(resSesa, dataViewRow);
				} catch (DataFormatException e) {
					LOG.error(e);
					e.printStackTrace();
				} catch (PSException e) {
					LOG.error(e);
					e.printStackTrace();
				}
				
			}
			
		}
		
		return dataviewsMap;
	}

	@SuppressWarnings("unchecked")
	public boolean removeDataViewRowList(String dataViewName) {
		boolean result = false;
		LOG.debug("Trying to remove data view row lists");

		try {
			this.global.lock();
			List<DataViewRow> dataViewRowList = this.session.getDataViewRowList(dataViewName, this.global);

			int size = dataViewRowList.size();

			for (int i = size - 1; i >= 0; i--) {
				DataViewRow dataViewRow = dataViewRowList.get(i);
				dataViewRowList.remove(dataViewRow);
				dataViewRow.remove();
			}

			this.global.save(false);
		} catch (PSException e) {
			LOG.error("Unexpected error while removing " + dataViewName, e);
			CUSTOM_LOG.error("Unexpected error while removing " + dataViewName + e);
			e.printStackTrace();
		} finally {
			
			try {
				this.global.unlock();
				result = true;
			} catch (PSException e) {
				LOG.error("Unexpected error while unlocking global ");
				CUSTOM_LOG.error("Unexpected error while unlocking global ");
				e.printStackTrace();
			}
			
		}

		return result;
	}

	public boolean removeDataViewRowList(String dataViewName, Resource resource, HashMap<String, Resource> toDelete) {
		return this.removeDataViewRowList(dataViewName, resource, false, false, toDelete);
	}

	@SuppressWarnings("unchecked")
	public boolean removeDataViewRowList(String dataViewName, Resource resource, boolean applyRejectedFilter,
			boolean applyApprovedFilter, HashMap<String, Resource> toDelete) {
		boolean result = false;
		DataViewRow row = null;

		try {
			
			resource.lock();
			List<DataViewRow> dataViewRowList = this.session.getDataViewRowList(dataViewName, resource);
			int size = dataViewRowList.size();
			LOG.debug(size + " rows found for the resource " + resource + ".");

			for (int i = size - 1; i >= 0; i--) {
				DataViewRow dataViewRow = dataViewRowList.get(i);
				row = dataViewRow;
				LOG.debug(" Processing record " + i + " out of " + size + ".");
				String rowSesa = row.getStringField(Constants.SESA_CODE);
				
				if (toDelete.get(rowSesa) != null) {
					LOG.debug("Resource " + rowSesa	+ " flagged as needing removal - removing it.");
					dataViewRowList.remove(dataViewRow);
					dataViewRow.remove();
					LOG.debug("row removed");
				}

			}

			LOG.debug(" Saving resource " + resource + ".");
			resource.save(false);
			LOG.debug("Resource saved");
		} catch (PSException e) {
			e.printStackTrace();

			try {
				
				if (row != null) {
					CUSTOM_LOG.error("1;" + row.getStringField(Constants.SESA_CODE) + ";11");
				} else {
					CUSTOM_LOG.error("1;" + resource.getStringField(Constants.SESA_CODE) + ";11");
				}
				
			} catch (PSException e1) {
				LOG.error(e1);
				e1.printStackTrace();
			}

			LOG.error("Unexpected error while removing " + dataViewName, e);
		} finally {
			
			try {
				resource.unlock();
				result = true;
			} catch (InternalFailure e) {
				LOG.error("Unexpected error while unlocking resource " + resource);
				LOG.error(e);
				e.printStackTrace();
			} catch (AccessException e) {
				LOG.error("Unexpected error while unlocking resource " + resource);
				LOG.error(e);
				e.printStackTrace();
			}
			
		}

		LOG.debug("Finished removing dataviewrowlist");
		return result;
	}

	public boolean updateRejectedResource(Resource res, String resSesa, DataViewRow row, String rowSesa,
			List<Resource> oldResourceList, HashMap<String, Resource> rowsToRemove) {
		boolean result = false;
		
		try {
			LOG.debug("Resource " + res + " has been found in the dataview, and flagged as Rejected. Deactivating the resource.");
			res.lock();
			String deactivatedId = this.deactivate(resSesa);
			LOG.debug("Generated ID for the deactivation: " + deactivatedId + ".");

			try {
				res.setStringField(Constants.SESA_CODE, deactivatedId);
				LOG.debug("Resource field " + Constants.SESA_CODE + " set as : " + deactivatedId + ".");
			} catch (PSException e) {
				throw new ResourceAlreadyExistsException("A resource with sesa code " + rowSesa + " "
						+ "already exists, thus the resource cannot be deactivated.");
			}

			res.setStringField(Constants.STATUS, Constants.INACTIVE);
			LOG.debug("Resource " + Constants.STATUS + " set to " + Constants.INACTIVE + ".");
			Calendar cal = Calendar.getInstance();
			cal.setTime(new Date());
			cal.add(Calendar.MONTH, 1);
			cal.set(Calendar.DATE, 1);
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.add(Calendar.DATE, -1);
			Date endOfCurrentMonth = cal.getTime();
			res.setDateField("End Date", endOfCurrentMonth);
			LOG.debug("End date of resource set to " + endOfCurrentMonth + ".");
			cal.add(Calendar.DATE, 1);
			Date endOfAvailability = cal.getTime();
			@SuppressWarnings("unchecked")
			List<DoubleDatedData> avail = res.getDatedData("Availability", DatedData.NONE);
			DoubleDatedData data = new DoubleDatedData(0, endOfAvailability, null);
			avail.add(data);
			res.setDatedData("Availability", avail);
			LOG.debug("Availability set from " + endOfAvailability + " as 0.");
			res.save(true);
			LOG.debug("Resource saved.");
			row.remove();
			oldResourceList.add(res);
			LOG.debug("Adding resource to the old resource list & flagged has 'to remove' for the postprocessing.");
			rowsToRemove.put(rowSesa, res);
			LOG.info("Resource " + resSesa + " has been deactivated because the request was rejected.");
		} catch (LockException e) {
			CUSTOM_LOG.warn("1;" + resSesa + ";9");
			LOG.error("Resource with sesa code " + resSesa + " is locked and cannot be updated.");
			e.printStackTrace();
		} catch (ResourceAlreadyExistsException raee) {
			CUSTOM_LOG.warn("1;" + resSesa + ";16");
			LOG.error(raee.getMessage());
			raee.printStackTrace();
			
			try {
				res.unlock();
			} catch (PSException e1) {
				LOG.error("Resource with sesa code " + resSesa + " cannot be unlocked.");
				LOG.error(e1);
				e1.printStackTrace();
			}

		} catch (PSException e) {
			CUSTOM_LOG.warn("1;" + resSesa + ";10");
			LOG.error("Resource with sesa code " + resSesa + " cannot be saved.");
			LOG.error(e);
			e.printStackTrace();
			
			try {
				res.unlock();
				result = true;
			} catch (PSException e1) {
				LOG.error("Resource with sesa code " + resSesa + " cannot be unlocked.");
				LOG.error(e1);
				e1.printStackTrace();
			}

		}

		return result;
	}

	private String deactivate(String string) {
		String newId = "";
		int zCount = 0;

		for (Iterator<String> iterator = this.resourcesMap.keySet().iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();

			String patternKey = "Z.*_" + string;
			Pattern pattern = Pattern.compile(patternKey);
			Matcher matcher = pattern.matcher(key);
			boolean found = matcher.find();

			if (found) {
				zCount++;
			}

		}

		newId = "Z" + (zCount + 1) + "_" + string;
		return newId;
	}
	
	public boolean updateApprovedResource(Resource res, String resSesa, DataViewRow row, String rowSesa,
			List<Resource> oldResourceList, HashMap<String, Resource> rowsToRemove, List<String> successSesaList,
			List<Resource> allDvResources) {
		boolean result = false;
		
		try {
			LOG.debug("Resource " + res + " has been found as Approved and needing modification. "
					+ "Locking the resource for modification");
			res.lock();

			try {
				LOG.debug("Modifying field " + Constants.SESA_CODE + " with value from dataview: "
						+ row.getStringField(Constants.SESA_CODE));
				res.setStringField(Constants.SESA_CODE, row.getStringField(Constants.SESA_CODE));
			} catch (PSException e) {
				throw new ResourceAlreadyExistsException(
						"A resource with sesa code " + row.getStringField(Constants.SESA_CODE)
								+ " already exists, thus the resource cannot be activated.");
			}

			LOG.debug("Modifying field " + Constants.STATUS + " with value " + Constants.ACTIVE);
			res.setStringField(Constants.STATUS, Constants.ACTIVE);
			LOG.debug("Modifying field " + Constants.WORKFLOW_STATE + " with value Done");
			row.setStringField(Constants.WORKFLOW_STATE, "Done");
			LOG.debug("Modifying field " + Constants.START_DATE + " with value from the dataview " + row.getDateField(Constants.NEW_START_DATE));
			res.setDateField(Constants.START_DATE, row.getDateField(Constants.NEW_START_DATE));
			LOG.debug("Listing this dataview row as to be removed.");
			rowsToRemove.put(row.getStringField(Constants.SESA_CODE), res);
			LOG.debug("Saving the resource.");
			res.save(true);
			LOG.info("Resource " + resSesa + " has been transfered successfully.");
			LOG.debug("Running through User list to update related user data.");
			User user = this.usersMap.get(row.getStringField(Constants.SESA_CODE));
			LOG.debug("Processing user: " + user + " because it's " + Constants.SESA_CODE + " was found in the dataview rows.");

			if (user != null) {

				try {
					LOG.debug("Locking user " + user + ".");
					user.lock();
					LOG.debug("Updating user field: " + Constants.LOGIN_ID + " from field " + Constants.SESA_CODE
							+ " of the dataview row - value " + row.getStringField(Constants.SESA_CODE) + ".");
					user.setStringField(Constants.LOGIN_ID, row.getStringField(Constants.SESA_CODE));
					LOG.debug("Updating user field: " + Constants.USER_ROLE + " from field "
							+ Constants.NEW_MSTT_USER_ROLE + " of the dataview row - value "
							+ row.getStringField(Constants.NEW_MSTT_USER_ROLE) + ".");
					user.setStringField(Constants.USER_ROLE, row.getStringField(Constants.NEW_MSTT_USER_ROLE));
					LOG.debug("Sauvegarde du user.");
					user.save(true);
					LOG.info("User " + resSesa + " has been set with <" + row.getStringField(Constants.NEW_MSTT_USER_ROLE) + "> access rights.");
					LOG.debug("User " + row.getStringField(Constants.SESA_CODE) + " flagged as successful for post-processing (to remove the successful records).");
					successSesaList.add(row.getStringField(Constants.SESA_CODE));
				} catch (LockException e) {
					LOG.info("User with sesa code " + resSesa + " is locked and cannot be updated.");
					CUSTOM_LOG.error("1;" + resSesa + ";5");
					e.printStackTrace();

					try {
						user.unlock();
					} catch (PSException e1) {
						LOG.info("User with sesa code " + resSesa + " cannot be unlocked.");
						LOG.error(e1);
						e1.printStackTrace();
					}

				} catch (PSException e) {
					CUSTOM_LOG.warn("1;" + resSesa + ";6");
					LOG.info("User with sesa code " + resSesa + " cannot be saved.");
					e.printStackTrace();
				}

			}

			allDvResources.add(res);
		} catch (LockException e) {
			CUSTOM_LOG.warn("1;" + resSesa + ";3");
			LOG.error("Resource with sesa code " + resSesa + " is locked and cannot be updated.");
			e.printStackTrace();
		} catch (ResourceAlreadyExistsException raee) {
			CUSTOM_LOG.warn("1;" + resSesa + ";15");
			LOG.error(raee.getMessage());
			raee.printStackTrace();

			try {
				res.unlock();
			} catch (PSException e1) {
				LOG.error("Resource with sesa code " + resSesa + " cannot be unlocked.");
				LOG.error(e1);
			}

		} catch (PSException e) {
			CUSTOM_LOG.warn("1;" + resSesa + ";4");
			LOG.error("Resource with sesa code " + resSesa + " cannot be saved.");
			e.printStackTrace();

			try {
				res.unlock();
			} catch (PSException e1) {
				LOG.error("Resource with sesa code " + resSesa + " cannot be unlocked.");
				LOG.error(e1);
				e1.printStackTrace();
			}

		} finally {
			result = true;
		}

		return result;
	}
	
}
