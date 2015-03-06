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

import java.util.Set;
import java.util.TreeSet;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.Parameter;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.probes.SQLQueryProbe;
import org.aim.artifacts.probes.StackTraceProbe;
import org.aim.artifacts.probes.ThreadTracingProbe;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.artifacts.scopes.JDBCScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.edc.strategies.RelativeQueryRTStrategy;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;
import org.spotter.shared.status.DiagnosisStatus;

public class EDCDetectionController extends AbstractDetectionController {

	private static final Logger LOGGER = LoggerFactory.getLogger(EDCDetectionController.class);

	public static final String KEY_EXPERIMENT_NAME = "experimentName";

	public static final String NAME_SINGLE_USER_EXP = "singleUserExp";
	public static final String NAME_MAIN_EXP = "mainExp";
	public static final String NAME_STACK_TRACE_EXP = "stackTraceExp";
	public static final String NAME_HIERARCHY_EXP = "hierarchyExp";

	private IEDCAnalysisStrategy strategy = new RelativeQueryRTStrategy();

	private boolean reuser = false;
	private double instrumentationGranularity;

	public EDCDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		reuser = Boolean.parseBoolean(this.getProblemDetectionConfiguration().getProperty(
				AbstractDetectionExtension.REUSE_EXPERIMENTS_FROM_PARENT, "false"));

		String sGranularity = getProblemDetectionConfiguration().getProperty(
				EDCExtension.INSTRUMENTATION_GRANULARITY_KEY,
				String.valueOf(EDCExtension.INSTRUMENTATION_GRANULARITY_DEFAULT));
		if (!sGranularity.matches("0|1|0.[0-9]+")) {
			instrumentationGranularity = EDCExtension.INSTRUMENTATION_GRANULARITY_DEFAULT;
		} else {
			instrumentationGranularity = Double.parseDouble(sGranularity);
		}

		strategy.init(getProblemDetectionConfiguration(), this);
	}

	@Override
	public long getExperimentSeriesDuration() {
		if (reuser) {
			return 0;
		} else {
			return Integer.parseInt(LpeStringUtils.getPropertyOrFail(GlobalConfiguration.getInstance().getProperties(),
					ConfigKeys.WORKLOAD_MAXUSERS, null)) * 4;
		}
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		if (!reuser) {
			int maxUsers = Integer.parseInt(LpeStringUtils.getPropertyOrFail(GlobalConfiguration.getInstance()
					.getProperties(), ConfigKeys.WORKLOAD_MAXUSERS, null));

			instrumentApplication(getServletHierarchyDescription());
			runExperiment(this, 1, NAME_HIERARCHY_EXP);
			uninstrumentApplication();

			instrumentApplication(getMainInstrumentationDescription(false));
			runExperiment(this, 1, NAME_SINGLE_USER_EXP);
			uninstrumentApplication();

			instrumentApplication(getMainInstrumentationDescription(true));
			runExperiment(this, maxUsers, NAME_MAIN_EXP);
			uninstrumentApplication();

			instrumentApplication(getStackTraceInstrDescription());
			runExperiment(this, 1, NAME_STACK_TRACE_EXP);
			uninstrumentApplication();
		}
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		strategy.setMeasurementData(data);
		return strategy.analyze();
	}


	private InstrumentationDescription getMainInstrumentationDescription(boolean useGranularity) {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.addProbe(ThreadTracingProbe.MODEL_PROBE).entityDone();
		idBuilder.newAPIScopeEntity(JDBCScope.class.getName()).addProbe(SQLQueryProbe.MODEL_PROBE)
				.addProbe(ResponsetimeProbe.MODEL_PROBE).addProbe(ThreadTracingProbe.MODEL_PROBE).entityDone();

		if (useGranularity) {
			idBuilder.newGlobalRestriction().setGranularity(instrumentationGranularity).restrictionDone();
		}

		return idBuilder.build();
	}

	private InstrumentationDescription getStackTraceInstrDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(JDBCScope.class.getName()).addProbe(StackTraceProbe.MODEL_PROBE)
				.addProbe(SQLQueryProbe.MODEL_PROBE).entityDone();

		return idBuilder.build();
	}

	private InstrumentationDescription getServletHierarchyDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();

		return idBuilder.build();
	}

	private void runExperiment(IDetectionController detectionController, int numUsers, String experimentName)
			throws WorkloadException, MeasurementException {
		LOGGER.info("{} detection controller started experiment with {} users ...", detectionController.getProvider()
				.getName(), numUsers);
		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_RAMP_UP);
		LoadConfig lConfig = new LoadConfig();
		lConfig.setNumUsers(numUsers);
		lConfig.setRampUpIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_INTERVAL_LENGTH));
		lConfig.setRampUpUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_NUM_USERS_PER_INTERVAL));
		lConfig.setCoolDownIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_INTERVAL_LENGTH));
		lConfig.setCoolDownUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_NUM_USERS_PER_INTERVAL));
		lConfig.setExperimentDuration(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_DURATION));
		getWorkloadAdapter().startLoad(lConfig);

		getWorkloadAdapter().waitForWarmupPhaseTermination();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_STABLE_PHASE);
		getMeasurementController().enableMonitoring();

		getWorkloadAdapter().waitForExperimentPhaseTermination();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_COOL_DOWN);
		getMeasurementController().disableMonitoring();

		getWorkloadAdapter().waitForFinishedLoad();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.COLLECTING_DATA);
		LOGGER.info("Storing data ...");
		long dataCollectionStart = System.currentTimeMillis();
		Parameter experimentNameParameter = new Parameter(KEY_EXPERIMENT_NAME, experimentName);
		Set<Parameter> parameters = new TreeSet<>();
		parameters.add(experimentNameParameter);
		getResultManager().storeResults(parameters, getMeasurementController());
		ProgressManager.getInstance()
				.addAdditionalDuration((System.currentTimeMillis() - dataCollectionStart) / SECOND);
		LOGGER.info("Data stored!");
	}

}
