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

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.lpe.common.extension.IExtension;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.result.model.ProblemOccurrence;
import org.spotter.shared.result.model.SpotterResult;

/**
 * A test detection controller.
 */
public class TestDetection extends AbstractDetectionController {

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            the provider of the extension
	 */
	public TestDetection(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {

	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(this, 1, new InstrumentationDescription());
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {

		SpotterResult result = new SpotterResult();
		String message = "Detected a test bottleneck!";

		String methodA = "methodA()";
		ProblemOccurrence occurrenceA = new ProblemOccurrence(methodA, message);
		String methodB = "methodB()";
		ProblemOccurrence occurrenceB = new ProblemOccurrence(methodB, message);
		String methodC = "methodC()";
		ProblemOccurrence occurrenceC = new ProblemOccurrence(methodC, message);

		result.addProblemOccurrence(occurrenceA);
		result.addProblemOccurrence(occurrenceB);
		result.addProblemOccurrence(occurrenceC);

		result.setDetected(true);
		result.addMessage("Test detection run finished successfully!");

		return result;
	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(1);
	}

}
