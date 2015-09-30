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
package org.spotter.ext.detection.est;

import org.lpe.common.config.ConfigParameterDescription;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

/**
 * Extension provider for the detection of Empty Semi Trucks.
 * 
 * @author Alexander Wert
 * 
 */
public class EmptySemiTrucksExtension extends AbstractDetectionExtension {

	// TODO: please provide a description
	private static final String EXTENSION_DESCRIPTION = "no description";

	@SuppressWarnings("unchecked")
	@Override
	public IDetectionController createExtensionArtifact(final String ... args) {
		return new EmptySemiTrucksDetectionController(this);
	}

	@Override
	public String getName() {
		return "EmptySemiTrucks";
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
	}

}
