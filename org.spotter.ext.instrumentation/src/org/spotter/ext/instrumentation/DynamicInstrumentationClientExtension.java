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
package org.spotter.ext.instrumentation;

import org.aim.api.instrumentation.description.internal.InstrumentationConstants;
import org.aim.artifacts.instrumentation.InstrumentationClient;
import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.instrumentation.AbstractInstrumentationExtension;
import org.spotter.core.instrumentation.ISpotterInstrumentation;

/**
 * Extension for dynamic instrumentation.
 * 
 * @author Alexander Wert
 * 
 */
public class DynamicInstrumentationClientExtension extends AbstractInstrumentationExtension {
	private static final String EXTENSION_DESCRIPTION = "The default instrumentation satellite adapter can be used to connect "
			+ "to a instrumentation satellite running in a JVM. This satellite adapter "
			+ "will instrument the JVM. \n"
			+ "The data collection with the instrumentation must be en-/disabled with a "
			+ "measurement satellite. Hence, you should not forget to configure a "
			+ "corresponding measurement satellite adapter.";

	@Override
	public String getName() {
		return "instrumentation.satellite.adapter.default";
	}

	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "Default Instrumentation Satellite Adapter";
	}

	private ConfigParameterDescription createPackagesToIncludeParameter() {
		ConfigParameterDescription packagesToIncludeParameter = new ConfigParameterDescription(
				ISpotterInstrumentation.INSTRUMENTATION_INCLUDES, LpeSupportedTypes.String);
		packagesToIncludeParameter.setASet(true);
		packagesToIncludeParameter.setDefaultValue("");
		packagesToIncludeParameter
				.setDescription("This parameter specifies the java packages whose classes should be considered for instrumentation. "
						+ "Class which are not in these packages will not be instrumented.");

		return packagesToIncludeParameter;
	}

	private ConfigParameterDescription createPackagesToExcludeParameter() {
		ConfigParameterDescription packagesToExcludeParameter = new ConfigParameterDescription(
				ISpotterInstrumentation.INSTRUMENTATION_EXCLUDES, LpeSupportedTypes.String);
		packagesToExcludeParameter.setASet(true);
		packagesToExcludeParameter.setDefaultValue(InstrumentationConstants.JAVA_PACKAGE + ","
				+ InstrumentationConstants.JAVAX_PACKAGE + "," + InstrumentationConstants.JAVASSIST_PACKAGE + ","
				+ InstrumentationConstants.AIM_PACKAGE + "," + InstrumentationConstants.LPE_COMMON_PACKAGE);
		packagesToExcludeParameter
				.setDescription("This parameter specifies the java packages whose classes should NOT be considered for instrumentation. "
						+ "Class which are in these packages will not be instrumented.");

		return packagesToExcludeParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createPackagesToIncludeParameter());
		addConfigParameter(createPackagesToExcludeParameter());
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
	}

	@Override
	public ISpotterInstrumentation createExtensionArtifact() {
		return new DynamicInstrumentationClient(this);
	}

	@Override
	public boolean testConnection(String host, String port) {
		return InstrumentationClient.testConnection(host, port);
	}

	@Override
	public boolean isRemoteExtension() {
		return true;
	}

}
