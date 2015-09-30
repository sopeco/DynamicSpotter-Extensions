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
import org.spotter.core.instrumentation.AbstractInstrumentationExtension;
import org.spotter.core.instrumentation.IInstrumentationAdapter;

public class TestInstrumentationExtension extends AbstractInstrumentationExtension {

	private static final String EXTENSION_DESCRIPTION = "The test instrumentation satellite adapter is used for test purposes only. The "
														+ "satellite adapter is a dummy and does nothing. The dummy will be removed after "
														+ "the first version has been officially released.";

	@SuppressWarnings("unchecked")
	@Override
	public IInstrumentationAdapter createExtensionArtifact(final String ... args) {
		return new TestInstrumentation(this);
	}

	@Override
	public String getName() {
		return "instrumentation.satellite.adapter.test";
	}

	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "Test Instrumentation Satellite Adapter";
	}
	
	@Override
	protected void initializeConfigurationParameters() {
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
