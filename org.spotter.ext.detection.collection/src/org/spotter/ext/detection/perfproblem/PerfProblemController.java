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
package org.spotter.ext.detection.perfproblem;

import java.util.List;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Detection controller for the generic Performance Problem.
 * 
 * @author Alexander Wert
 * 
 */
public class PerfProblemController extends AbstractDetectionController {

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider.
	 */
	public PerfProblemController(IExtension<IDetectionController> provider) {
		super(provider);

	}

	@Override
	protected void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(this, 1, getInstrumentationDescription());
	}

	@Override
	public void loadProperties() {

	}

	private InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();
		return idBuilder.build();
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {

		double perfReqThreshold = GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.PERFORMANCE_REQUIREMENT_THRESHOLD, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_THRESHOLD);
		double perfReqConfidence = GlobalConfiguration.getInstance().getPropertyAsDouble(
				ConfigKeys.PERFORMANCE_REQUIREMENT_CONFIDENCE, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_CONFIDENCE);

		SpotterResult result = new SpotterResult();
		result.setDetected(false);

		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no results for the given scope!");
			return result;
		}

		for (String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {
			ParameterSelection selectOperation = new ParameterSelection().select(ResponseTimeRecord.PAR_OPERATION,
					operation);
			Dataset operationSpecificDataset = selectOperation.applyTo(rtDataset);
			List<Long> responseTimes = operationSpecificDataset.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME,
					Long.class);
			int reqViolationsCount = countRequirementViolations(perfReqThreshold, responseTimes);

			double percentageViolations = ((double) reqViolationsCount) / ((double) responseTimes.size());
			if (percentageViolations > 1.0 - perfReqConfidence) {
				result.addMessage("Performance Problem detected in operation: " + operation);
				result.setDetected(true);
			}

		}

		return result;

	}

	private int countRequirementViolations(double perfReqThreshold, List<Long> responseTimes) {
		int count = 0;
		for (Long rt : responseTimes) {
			if (rt > perfReqThreshold) {
				count++;
			}
		}
		return count;
	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(1);

	}

}
