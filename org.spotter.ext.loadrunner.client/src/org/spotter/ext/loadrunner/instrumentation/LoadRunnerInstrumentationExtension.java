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
package org.spotter.ext.loadrunner.instrumentation;

import org.lpe.common.loadgenerator.LoadGeneratorClient;
import org.spotter.core.instrumentation.AbstractInstrumentationExtension;

/**
 * Extension for LoadRunner instrumentation.
 * @author Alexander Wert
 *
 */
public class LoadRunnerInstrumentationExtension extends AbstractInstrumentationExtension {

	public LoadRunnerInstrumentationExtension() {
		super(LoadRunnerInstrumentationClient.class);
	}

	private static final String EXTENSION_DESCRIPTION = "The loadrunner instrumentation satellite adapter can be used to "
														+ "connect to an HP Loadrunner instrumentation satellite. It will only "
														+ "be applicable if you have a Loadrunner as a workload generator.";
	
	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "LoadRunner Instrumentation Satellite Adapter";
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
	}

	@Override
	public boolean testConnection(final String host, final String port) {
		return LoadGeneratorClient.testConnection(host, port);
	}

	@Override
	public boolean isRemoteExtension() {
		return true;
	}

}
