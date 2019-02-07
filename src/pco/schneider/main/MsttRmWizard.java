package pco.schneider.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import pco.schneider.api.WizardManager;

public class MsttRmWizard {

	private static final Logger LOG = Logger.getLogger(MsttRmWizard.class);

	public static void main(String[] args) {
		int breakCode = 0;
		int action = 0;

		if (args.length >= 2) {
			String cmd = args[0];
			String CONF_DIRECTORY = args[1];
			File confDirectory = new File(CONF_DIRECTORY);

			if (!confDirectory.exists()) {
				LOG.error("Configuration directory " + CONF_DIRECTORY + " does not exists.");
				LOG.error("Make sure to provide a consistent configuration folder.");
				System.out.println("Configuration directory " + CONF_DIRECTORY + " does not exists.");
				System.out.println("Make sure to provide a consistent configuration folder.");
				printUsage();
				breakCode = 2;
			}

			PropertyConfigurator.configure(CONF_DIRECTORY + File.separator + Constants.LOG_FILE);

			LOG.info(Constants.APP_INFO);
			LOG.info("Demarrage de la passerelle en mode " + cmd);
			System.out.println(Constants.APP_INFO);
			System.out.println("Demarrage de la passerelle en mode " + cmd);
			
			if ("transfer".equals(cmd)) {
				action = Constants.ACTION_TRANSFER;
			} else if ("ldap".equals(cmd)) {
				action = Constants.ACTION_LDAP;
			} else {
				printExitError();
				breakCode = 3;
			}

			Properties props = readPropertiesFromFile(CONF_DIRECTORY + File.separator + Constants.PSCONNECT_FILE);
			WizardManager rmWiz = new WizardManager(props);
			breakCode = rmWiz.execute(action, cmd, CONF_DIRECTORY);
		} else {
			printExitError();
			breakCode = 1;
			
		}

		System.exit(breakCode);
		System.out.println("Exit code: " + breakCode);
		LOG.info("Exit code: " + breakCode);
	}

	private static void printExitError() {
		LOG.error("Invalid command line arguments.");
		LOG.error("");
		System.out.println("Invalid command line arguments.");
		System.out.println("");
		printUsage();
	}

	private static void printUsage() {
		LOG.error("");
		LOG.error("Usage : ");
		LOG.error("MsttRmWizard [action] [configuration folder]");
		LOG.error("");
		LOG.error("action                    = ldap or transfer");
		LOG.error("configuration folder      = path to all configuration files");
		LOG.error("");
		System.out.println("");
		System.out.println("Usage : ");
		System.out.println("MsttRmWizard [action] [configuration folder]");
		System.out.println("");
		System.out.println("action                    = ldap or transfer");
		System.out.println("configuration folder      = path to all configuration files");
		System.out.println("");
	}

	private static Properties readPropertiesFromFile(final String path) {
		Properties properties = new Properties();
		File file = new File(path);

		try {
			InputStream resourceAsStream = new FileInputStream(file);
			properties.load(resourceAsStream);
		} catch (FileNotFoundException e) {
			LOG.error(e);
			System.out.println(e);
			e.printStackTrace();
		} catch (IOException e) {
			LOG.error(e);
			System.out.println(e);
			e.printStackTrace();
		}
		
		return properties;
	}

}
