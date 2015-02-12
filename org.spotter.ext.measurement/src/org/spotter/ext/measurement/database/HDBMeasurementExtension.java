package org.spotter.ext.measurement.database;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.measurement.AbstractMeasurmentExtension;
import org.spotter.core.measurement.IMeasurementAdapter;

public class HDBMeasurementExtension extends AbstractMeasurmentExtension {
	public static final String HOST_KEY = "host";
	public static final String PORT_KEY = "port";
	public static final String USER_NAME_KEY = "username";
	public static final String PASSWORD_KEY = "password";

	@Override
	public String getName() {
		return "HDB Measurement";
	}

	@Override
	public IMeasurementAdapter createExtensionArtifact() {
		return new HDBMeasurement(this);
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createHostParameter());
		addConfigParameter(createPortParameter());
		addConfigParameter(createUsernameParameter());
		addConfigParameter(createPasswordParameter());
	}

	@Override
	public boolean testConnection(String host, String port) {
		try {
			HDBMeasurement instance = (HDBMeasurement) createExtensionArtifact();

			instance.initialize();
			boolean con = instance.testConnection();
			instance.disableMonitoring();
			return con;
		} catch (Exception e) {
			return false;
		}

	}

	@Override
	public boolean isRemoteExtension() {
		return true;
	}

	

	private ConfigParameterDescription createHostParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(HOST_KEY, LpeSupportedTypes.String);
		parameter.setMandatory(true);
		parameter.setDescription("The database host");

		return parameter;
	}

	private ConfigParameterDescription createPortParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(PORT_KEY, LpeSupportedTypes.Integer);
		parameter.setMandatory(true);
		parameter.setDescription("The database port");

		return parameter;
	}

	private ConfigParameterDescription createUsernameParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(USER_NAME_KEY, LpeSupportedTypes.String);
		parameter.setMandatory(true);
		parameter.setDescription("The user name");

		return parameter;
	}

	private ConfigParameterDescription createPasswordParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(PASSWORD_KEY, LpeSupportedTypes.String);
		parameter.setMandatory(true);
		parameter.setDescription("The user's password");

		return parameter;
	}

}
