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

import org.spotter.core.detection.AbstractDetectionExtension;

/**
 * Extension provider for the detection of Empty Semi Trucks.
 * 
 * @author Alexander Wert
 * 
 */
public class EmptySemiTrucksExtension extends AbstractDetectionExtension {
	public EmptySemiTrucksExtension() {
		super(EmptySemiTrucksDetectionController.class);
	}

	private static final String EXTENSION_DESCRIPTION = 
			"The Empty Semi Trucks occurs in software systems "
			+ "where a big amount of requests is needed for a single job.";

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.lpe.common.extension.ReflectiveAbstractExtension#getDescription()
	 */
	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}

	@Override
	protected void initializeConfigurationParameters() {
	}

}
