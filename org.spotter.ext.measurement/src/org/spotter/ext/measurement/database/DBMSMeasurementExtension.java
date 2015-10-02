/**
 * Copyright 2014 SAP AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spotter.ext.measurement.database;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.measurement.AbstractMeasurmentExtension;

/**
 * Extension for measurement / sampling of a DBMS.
 * 
 * @author Alexander Wert
 * 
 */

public class DBMSMeasurementExtension extends AbstractMeasurmentExtension {

	public DBMSMeasurementExtension() {
		super(DBMSMeasurement.class);
	}

	private static final String EXTENSION_DESCRIPTION = "The DBMS sampling measurement satellite adapter is used to connect "
			+ "to a MySQL DBMS and to query the database status.";

	public static final String HOST = "host";
	public static final String PORT = "port";
	public static final String USER = "user";
	public static final String PASSWORD = "password";
	public static final String DATABASE = "database";

	public static final String CONNECTION_STRING = "org.spotter.sampling.mysql.connectionString";

	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "DBMS Sampling Measurement Satellite Adapter";
	}

	private ConfigParameterDescription createHostParameter() {
		final ConfigParameterDescription samplingDelayParameter = new ConfigParameterDescription(HOST,
				LpeSupportedTypes.String);
		samplingDelayParameter.setMandatory(true);
		samplingDelayParameter.setDescription("Host");

		return samplingDelayParameter;
	}

	private ConfigParameterDescription createPortParameter() {
		final ConfigParameterDescription samplingDelayParameter = new ConfigParameterDescription(PORT,
				LpeSupportedTypes.String);
		samplingDelayParameter.setDefaultValue("3306");
		samplingDelayParameter.setMandatory(true);
		samplingDelayParameter.setDescription("Port");

		return samplingDelayParameter;
	}

	private ConfigParameterDescription createUserParameter() {
		final ConfigParameterDescription samplingDelayParameter = new ConfigParameterDescription(USER,
				LpeSupportedTypes.String);
		samplingDelayParameter.setMandatory(true);
		samplingDelayParameter.setDescription("User");

		return samplingDelayParameter;
	}

	private ConfigParameterDescription createPasswordParameter() {
		final ConfigParameterDescription samplingDelayParameter = new ConfigParameterDescription(PASSWORD,
				LpeSupportedTypes.String);
		samplingDelayParameter.setMandatory(true);
		samplingDelayParameter.setDescription("Password");

		return samplingDelayParameter;
	}

	private ConfigParameterDescription createDatabaseParameter() {
		final ConfigParameterDescription samplingDelayParameter = new ConfigParameterDescription(DATABASE,
				LpeSupportedTypes.String);
		samplingDelayParameter.setMandatory(true);
		samplingDelayParameter.setDescription("Database Name");

		return samplingDelayParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createHostParameter());
		addConfigParameter(createPortParameter());
		addConfigParameter(createUserParameter());
		addConfigParameter(createPasswordParameter());
		addConfigParameter(createDatabaseParameter());
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
	}

	@Override
	public boolean testConnection(final String host, final String port) {
		return true;
	}

	@Override
	public boolean isRemoteExtension() {
		return false;
	}

}
