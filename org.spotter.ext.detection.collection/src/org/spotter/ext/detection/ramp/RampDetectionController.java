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
package org.spotter.ext.detection.ramp;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.description.InstrumentationDescription;
import org.lpe.common.extension.IExtension;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.measurement.IMeasurementAdapter;
import org.spotter.core.workload.IWorkloadAdapter;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.ramp.strategies.DirectGrowthStrategy;
import org.spotter.ext.detection.ramp.strategies.LinearRegressionStrategy;
import org.spotter.ext.detection.ramp.strategies.TimeWindowsStrategy;
import org.spotter.shared.result.model.SpotterResult;

/**
 * The Ramp antipattern detection controller.
 * 
 * @author Alexander Wert
 * 
 */
public class RampDetectionController extends AbstractDetectionController {

	private String analysisStrategy;
	private IRampDetectionStrategy analysisStrategyImpl;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider.
	 */
	public RampDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException {
		analysisStrategyImpl.executeExperiments();

	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		return analysisStrategyImpl.analyze(data);
	}

	@Override
	public void loadProperties() {
		analysisStrategy = getProblemDetectionConfiguration().getProperty(RampExtension.DETECTION_STRATEGY_KEY,
				RampExtension.TIME_WINDOWS_STRATEGY);

		switch (analysisStrategy) {
		case RampExtension.TIME_WINDOWS_STRATEGY:
			analysisStrategyImpl = new TimeWindowsStrategy();
			break;
		case RampExtension.DIRECT_GROWTH_STRATEGY:
			analysisStrategyImpl = new DirectGrowthStrategy();
			break;
		case RampExtension.LIN_REGRESSION_STRATEGY:
			analysisStrategyImpl = new LinearRegressionStrategy();
			break;
		default:
			analysisStrategyImpl = new TimeWindowsStrategy();
		}
		analysisStrategyImpl.setMainDetectionController(this);
		analysisStrategyImpl.setProblemDetectionConfiguration(getProblemDetectionConfiguration());
	}

	@Override
	public long getExperimentSeriesDuration() {
		return analysisStrategyImpl.getExperimentSeriesDuration();
	}

	/**
	 * Instrument application.
	 * 
	 * @param descr
	 *            description
	 */
	public void instrument(InstrumentationDescription descr) {
		try {
			instrumentApplication(descr);
		} catch (InstrumentationException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Uninstrument application.
	 */
	public void uninstrument() {
		try {
			uninstrumentApplication();
		} catch (InstrumentationException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 
	 * @return the worlöoad adapter
	 */
	public IWorkloadAdapter workloadAdapter() {
		return getWorkloadAdapter();
	}

	/**
	 * 
	 * @return the measurement adapter
	 */
	public IMeasurementAdapter measurementAdapter() {
		return getMeasurementController();
	}

	/**
	 * Executes experiment with high load using the passed instrumentation
	 * description.
	 * 
	 * @param descr
	 *            instrumentation description
	 */
	public void executeHighLoadExperiment(InstrumentationDescription descr) {

		try {
			executeDefaultExperimentSeries(this, 1, descr);
		} catch (InstrumentationException | MeasurementException | WorkloadException e) {
			throw new RuntimeException(e);
		}

	}

}
