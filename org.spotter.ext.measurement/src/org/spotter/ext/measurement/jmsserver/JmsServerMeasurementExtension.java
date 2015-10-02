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
package org.spotter.ext.measurement.jmsserver;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.extension.IExtensionArtifact;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.measurement.AbstractMeasurmentExtension;

/**
 * Extension for JMS server sampler.
 * 
 * @author Alexander Wert
 * 
 */
public class JmsServerMeasurementExtension extends AbstractMeasurmentExtension {

	public JmsServerMeasurementExtension() {
		super(JmsServerMeasurement.class);
	}

	private static final String EXTENSION_DESCRIPTION = "The jmsserver sampling measurement satellite adapter is used "
														+ "to connect to the special sampling satellites for Java Messaging "
														+ "Service (JMS) server. They sample more than the default sampling "
														+ "satellites.";

	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "JMSServer Sampling Measurement Satellite Adapter";
	}

	private ConfigParameterDescription createServerConnectionStringParameter() {
		final ConfigParameterDescription collectorTypeParameter = new ConfigParameterDescription(
				JmsServerMeasurement.ACTIVE_MQJMX_URL, LpeSupportedTypes.String);
		collectorTypeParameter.setMandatory(true);
		collectorTypeParameter.setDescription("Connection string to the JMX interface of the massaging service.");

		return collectorTypeParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createServerConnectionStringParameter());
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
