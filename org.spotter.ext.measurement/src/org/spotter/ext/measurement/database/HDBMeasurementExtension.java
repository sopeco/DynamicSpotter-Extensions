package org.spotter.ext.measurement.database;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.extension.IExtensionArtifact;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.measurement.AbstractMeasurmentExtension;

public class HDBMeasurementExtension extends AbstractMeasurmentExtension {
	public HDBMeasurementExtension() {
		super(HDBMeasurement.class);
	}

	public static final String HOST_KEY = "host";
	public static final String PORT_KEY = "port";
	public static final String USER_NAME_KEY = "username";
	public static final String PASSWORD_KEY = "password";

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createHostParameter());
		addConfigParameter(createPortParameter());
		addConfigParameter(createUsernameParameter());
		addConfigParameter(createPasswordParameter());
	}

	@Override
	public boolean testConnection(final String host, final String port) {
		try {
			final HDBMeasurement instance = (HDBMeasurement) createExtensionArtifact();

			instance.initialize();
			final boolean con = instance.testConnection();
			instance.disableMonitoring();
			return con;
		} catch (final Exception e) {
			return false;
		}

	}

	@Override
	public boolean isRemoteExtension() {
		return true;
	}

	

	private ConfigParameterDescription createHostParameter() {
		final ConfigParameterDescription parameter = new ConfigParameterDescription(HOST_KEY, LpeSupportedTypes.String);
		parameter.setMandatory(true);
		parameter.setDescription("The database host");

		return parameter;
	}

	private ConfigParameterDescription createPortParameter() {
		final ConfigParameterDescription parameter = new ConfigParameterDescription(PORT_KEY, LpeSupportedTypes.Integer);
		parameter.setMandatory(true);
		parameter.setDescription("The database port");

		return parameter;
	}

	private ConfigParameterDescription createUsernameParameter() {
		final ConfigParameterDescription parameter = new ConfigParameterDescription(USER_NAME_KEY, LpeSupportedTypes.String);
		parameter.setMandatory(true);
		parameter.setDescription("The user name");

		return parameter;
	}

	private ConfigParameterDescription createPasswordParameter() {
		final ConfigParameterDescription parameter = new ConfigParameterDescription(PASSWORD_KEY, LpeSupportedTypes.String);
		parameter.setMandatory(true);
		parameter.setDescription("The user's password");

		return parameter;
	}

}
