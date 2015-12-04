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
package org.spotter.ext.detection.edc;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;


public class EDCExtension extends AbstractDetectionExtension {

	public EDCExtension() {
		super(EDCDetectionController.class);
	}

	private static final String EXTENSION_DESCRIPTION = "Expensive Database Calls are single, long-running database request. "
			+ "They cause high overhead at the database, either due to high locking times, many locks, "
			+ "or high utilization of the database's resources.";
	public static final String INSTRUMENTATION_GRANULARITY_KEY = "instrumentationGranularity";
	public static final String PERF_REQ_RELATIVE_QUERY_RT_KEY = "perfReqRelativeQueryRT";
	public static final String PERF_REQ_RELATIVE_QUERY_RT_DIFF_KEY = "perfReqRelativeQueryRTDiff";

	public static final double INSTRUMENTATION_GRANULARITY_DEFAULT = 0.01;
	public static final double PERF_REQ_RELATIVE_QUERY_RT_DEFAULT = 0.5;
	public static final double PERF_REQ_RELATIVE_QUERY_RT_DIFF_DEFAULT = 0.0;

	private ConfigParameterDescription createInstrumentationGranularityParameter() {
		final ConfigParameterDescription instrumentationGranularityParameter = new ConfigParameterDescription(
				INSTRUMENTATION_GRANULARITY_KEY, LpeSupportedTypes.Double);
		instrumentationGranularityParameter.setDefaultValue(String.valueOf(INSTRUMENTATION_GRANULARITY_DEFAULT));
		instrumentationGranularityParameter.setRange(String.valueOf(0), String.valueOf(1));
		instrumentationGranularityParameter
				.setDescription("Instrumentation granularity to be used while running high load between 0 and 1.");

		return instrumentationGranularityParameter;
	}

	private ConfigParameterDescription createPerfReqRelativeQueryRTParameter() {
		final ConfigParameterDescription perfReqRelativeQueryRTParameter = new ConfigParameterDescription(
				PERF_REQ_RELATIVE_QUERY_RT_KEY, LpeSupportedTypes.Double);
		perfReqRelativeQueryRTParameter.setDefaultValue(String.valueOf(PERF_REQ_RELATIVE_QUERY_RT_DEFAULT));
		perfReqRelativeQueryRTParameter.setRange(String.valueOf(0), String.valueOf(1));
		perfReqRelativeQueryRTParameter
				.setDescription("Performance requirement for relative (to calling servlet response time) query response time.");

		return perfReqRelativeQueryRTParameter;
	}

	private ConfigParameterDescription createPerfReqRelativeQueryRTDiffParameter() {
		final ConfigParameterDescription perfReqRelativeQueryRTParameter = new ConfigParameterDescription(
				PERF_REQ_RELATIVE_QUERY_RT_DIFF_KEY, LpeSupportedTypes.Double);
		perfReqRelativeQueryRTParameter.setDefaultValue(String.valueOf(PERF_REQ_RELATIVE_QUERY_RT_DIFF_DEFAULT));
		perfReqRelativeQueryRTParameter.setRange(String.valueOf(-1), String.valueOf(1));
		perfReqRelativeQueryRTParameter
				.setDescription("Performance requirement for difference of relative (to calling servlet response time) query response times when running one user and when running high load.");

		return perfReqRelativeQueryRTParameter;
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
		addConfigParameter(createInstrumentationGranularityParameter());
		addConfigParameter(createPerfReqRelativeQueryRTParameter());
		addConfigParameter(createPerfReqRelativeQueryRTDiffParameter());
	}

}
