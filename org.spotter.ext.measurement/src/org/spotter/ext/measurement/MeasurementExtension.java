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
package org.spotter.ext.measurement;

import org.aim.artifacts.client.JMXAdaptiveInstrumentationClient;
import org.lpe.common.config.ConfigParameterDescription;
import org.spotter.core.measurement.AbstractMeasurmentExtension;
import org.spotter.core.measurement.IMeasurementAdapter;

/**
 * Extension for generic measurement REST client.
 * 
 * @author Alexander Wert
 * 
 */
public class MeasurementExtension extends AbstractMeasurmentExtension {
	
	public MeasurementExtension() {
		super(MeasurementClient.class);
	}

	private static final String EXTENSION_DESCRIPTION = "The measurement satellite adapter is used to connect to system where "
														+ "instrumentation is possible. This satellite adapter can enable and "
														+ "disable the collecting of data fetched with instrumentation. "
														+ "This satellite adapter will be mainly used on systems where "
														+ "a instrumentation satellite is running. \n"
														+ "In addition this satellite adapter comprises the sampling of "
														+ "hardware utilization.";
	
	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "Instrumentation Measurement Satellite Adapter";
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
	}

	@SuppressWarnings("unchecked")
	@Override
	public IMeasurementAdapter createExtensionArtifact(final Object ... args) {
		final IMeasurementAdapter mController = new MeasurementClient(this);
		for (final ConfigParameterDescription cpd : this.getConfigParameters()) {
			if (cpd.getDefaultValue() != null) {
				mController.getProperties().setProperty(cpd.getName(), cpd.getDefaultValue());
			}
		}
		return mController;
	}

	@Override
	public boolean testConnection(final String host, final String port) {
		return JMXAdaptiveInstrumentationClient.testConnection(host, port);
	}

	@Override
	public boolean isRemoteExtension() {
		return true;
	}

}
