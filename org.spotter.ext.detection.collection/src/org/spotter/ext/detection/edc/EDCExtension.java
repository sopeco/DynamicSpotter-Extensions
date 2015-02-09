package org.spotter.ext.detection.edc;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

public class EDCExtension extends AbstractDetectionExtension {

	private static final String EXTENSION_DESCRIPTION = "An expensive database call. ";
	public static final String INSTRUMENTATION_GRANULARITY_KEY = "instrumentationGranularity";
	public static final String PERF_REQ_RELATIVE_QUERY_RT_KEY = "perfReqRelativeQueryRT";
	public static final String PERF_REQ_RELATIVE_QUERY_RT_DIFF_KEY = "perfReqRelativeQueryRTDiff";

	public static final double INSTRUMENTATION_GRANULARITY_DEFAULT = 0.01;
	public static final double PERF_REQ_RELATIVE_QUERY_RT_DEFAULT = 0.5;
	public static final double PERF_REQ_RELATIVE_QUERY_RT_DIFF_DEFAULT = 0.0;

	@Override
	public IDetectionController createExtensionArtifact() {
		return new EDCDetectionController(this);
	}

	@Override
	public String getName() {
		return "Expensive Database Call";
	}

	private ConfigParameterDescription createInstrumentationGranularityParameter() {
		ConfigParameterDescription instrumentationGranularityParameter = new ConfigParameterDescription(
				INSTRUMENTATION_GRANULARITY_KEY, LpeSupportedTypes.Double);
		instrumentationGranularityParameter.setDefaultValue(String.valueOf(INSTRUMENTATION_GRANULARITY_DEFAULT));
		instrumentationGranularityParameter.setRange(String.valueOf(0), String.valueOf(1));
		instrumentationGranularityParameter
				.setDescription("Instrumentation granularity to be used while running high load between 0 and 1.");

		return instrumentationGranularityParameter;
	}

	private ConfigParameterDescription createPerfReqRelativeQueryRTParameter() {
		ConfigParameterDescription perfReqRelativeQueryRTParameter = new ConfigParameterDescription(
				PERF_REQ_RELATIVE_QUERY_RT_KEY, LpeSupportedTypes.Double);
		perfReqRelativeQueryRTParameter.setDefaultValue(String.valueOf(PERF_REQ_RELATIVE_QUERY_RT_DEFAULT));
		perfReqRelativeQueryRTParameter.setRange(String.valueOf(0), String.valueOf(1));
		perfReqRelativeQueryRTParameter
				.setDescription("Performance requirement for relative (to calling servlet response time) query response time.");

		return perfReqRelativeQueryRTParameter;
	}

	private ConfigParameterDescription createPerfReqRelativeQueryRTDiffParameter() {
		ConfigParameterDescription perfReqRelativeQueryRTParameter = new ConfigParameterDescription(
				PERF_REQ_RELATIVE_QUERY_RT_DIFF_KEY, LpeSupportedTypes.Double);
		perfReqRelativeQueryRTParameter.setDefaultValue(String.valueOf(PERF_REQ_RELATIVE_QUERY_RT_DIFF_DEFAULT));
		perfReqRelativeQueryRTParameter.setRange(String.valueOf(0), String.valueOf(1));
		perfReqRelativeQueryRTParameter
				.setDescription("Performance requirement for difference of relative (to calling servlet response time) query response times when running one user and when running high load.");

		return perfReqRelativeQueryRTParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createInstrumentationGranularityParameter());
		addConfigParameter(createPerfReqRelativeQueryRTParameter());
		addConfigParameter(createPerfReqRelativeQueryRTDiffParameter());
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
	}

}
