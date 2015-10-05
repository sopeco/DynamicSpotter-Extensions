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
import org.spotter.core.workload.AbstractWorkloadExtension;

public class TestLoadDriverExtension extends  AbstractWorkloadExtension  {

	public TestLoadDriverExtension() {
		super(TestLoadDriver.class);
	}

	private static final String EXTENSION_DESCRIPTION = "The test workload satellite adapter is used for test purposes only. The "
														+ "satellite adapter is a dummy and does nothing. The dummy will be removed after "
														+ "the first version has been officially released.";

	public static final String NUM_EXPERIMENTS = "org.spotter.test.numExperiments";

	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "Test Workload Satellite Adapter";
	}

	/* (non-Javadoc)
	 * @see org.lpe.common.extension.ReflectiveAbstractExtension#getDescription()
	 */
	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}
	
	@Override
	protected void initializeConfigurationParameters() {
		final ConfigParameterDescription par = new ConfigParameterDescription(NUM_EXPERIMENTS, LpeSupportedTypes.Integer);
		par.setMandatory(false);
		par.setDefaultValue(String.valueOf(100));
		par.setDescription("Number of experiments.");
		addConfigParameter(par);
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
