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

import java.util.Collections;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.description.instrumentation.InstrumentationEntity;
import org.aim.aiminterface.description.restriction.Restriction;
import org.aim.aiminterface.description.sampling.SamplingDescription;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.lpe.common.extension.IExtension;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
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
	public TestDetection(final IExtension provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {

	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(this, 1, new InstrumentationDescription(Collections.<InstrumentationEntity> emptySet(),Collections.<SamplingDescription> emptySet(),Restriction.EMPTY_RESTRICTION));
	}

	@Override
	protected SpotterResult analyze(final DatasetCollection data) {

		final SpotterResult result = new SpotterResult();
		final String message = "Detected a test bottleneck!";

		final String methodA = "methodA()";
		final ProblemOccurrence occurrenceA = new ProblemOccurrence(methodA, message);
		final String methodB = "methodB()";
		final ProblemOccurrence occurrenceB = new ProblemOccurrence(methodB, message);
		final String methodC = "methodC()";
		final ProblemOccurrence occurrenceC = new ProblemOccurrence(methodC, message);

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
