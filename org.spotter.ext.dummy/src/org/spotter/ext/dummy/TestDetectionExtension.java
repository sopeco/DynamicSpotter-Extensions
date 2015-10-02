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
package org.spotter.ext.dummy;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

public class TestDetectionExtension extends AbstractDetectionExtension {

	public TestDetectionExtension() {
		super(TestDetection.class);
	}

	private static final String EXTENSION_DESCRIPTION = "This is just a dummy extension doing dummy experiments.";

	@Override
	protected void initializeConfigurationParameters() {
		final ConfigParameterDescription par = new ConfigParameterDescription("testParameter", LpeSupportedTypes.Integer);
		par.setMandatory(false);
		par.setDefaultValue(String.valueOf(100));
		par.setDescription("It's just a test parameter.");
		addConfigParameter(par);
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
	}

}
