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
package org.spotter.ext.measurement.resourcemonitoring;

import org.aim.resourcemonitoring.ResourceMonitoringClient;
import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.extension.IExtensionArtifact;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.measurement.AbstractMeasurmentExtension;

/**
 * Extension for the resource monitoring client.
 * 
 * @author Alexander Wert
 * 
 */
public class ResourceMonitoringExtension extends AbstractMeasurmentExtension {
	
	public ResourceMonitoringExtension() {
		super(ResourceMonitoringAdapter.class);
	}

	private static final String EXTENSION_DESCRIPTION = "The sampling measurement satellite adapter is used to connect "
														+ "to all sampling satellites.";

	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "Sampling Measurement Satellite Adapter";
	}
	
	private ConfigParameterDescription createSamplingDelayParameter() {
		final ConfigParameterDescription samplingDelayParameter = new ConfigParameterDescription(
				ResourceMonitoringAdapter.SAMPLING_DELAY, LpeSupportedTypes.Long);
		samplingDelayParameter.setDefaultValue(String.valueOf(ResourceMonitoringAdapter.DEFAULT_DELAY));
		samplingDelayParameter.setDescription("The sampling interval in milliseconds.");

		return samplingDelayParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createSamplingDelayParameter());
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
	}

	@Override
	public boolean testConnection(final String host, final String port) {
		return ResourceMonitoringClient.testConnection(host, port);
	}

	@Override
	public boolean isRemoteExtension() {
		return true;
	}
}
