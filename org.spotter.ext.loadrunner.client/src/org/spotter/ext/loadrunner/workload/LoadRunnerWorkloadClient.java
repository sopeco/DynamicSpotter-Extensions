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
package org.spotter.ext.loadrunner.workload;

import org.lpe.common.extension.IExtension;
import org.lpe.common.loadgenerator.LoadGeneratorClient;
import org.lpe.common.loadgenerator.config.LGWorkloadConfig;
import org.lpe.common.loadgenerator.scenario.SchedulingMode;
import org.lpe.common.loadgenerator.scenario.VUserInitializationMode;
import org.lpe.common.util.LpeStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.loadrunner.LRConfigKeys;

/**
 * The client to communicate with the LoadRunner server.
 * 
 * @author Le-Huan Stefan Tran
 */

public class LoadRunnerWorkloadClient extends AbstractWorkloadAdapter {
	private static final int POLLING_INTERVAL = 500;

	private static final int MILLIS_IN_SECOND = 1000;

	private static final Logger LOGGER = LoggerFactory
			.getLogger(LoadRunnerWorkloadClient.class);

	private LoadGeneratorClient lrClient;
	private long experimentStartTime;
	private long rampUpDuration;
	private long experimentDuration;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */

	public LoadRunnerWorkloadClient(final IExtension provider) {
		super(provider);

	}

	@Override
	public void initialize() throws WorkloadException {
		if (lrClient == null) {
			lrClient = new LoadGeneratorClient(getHost(), getPort());
			if (!lrClient.testConnection()) {
				throw new WorkloadException(
						"Connection to loadrunner could not be established!");
			}
		}
	}

	@Override
	public void startLoad(final LoadConfig loadConfig) throws WorkloadException {
		final LGWorkloadConfig lrConfig = createLRConfig(loadConfig);
		LOGGER.info("Triggered load with {} users ...", lrConfig.getNumUsers());
		experimentStartTime = System.currentTimeMillis();
		rampUpDuration = calculateActualRampUpDuration(lrConfig)
				* MILLIS_IN_SECOND;
		experimentDuration = lrConfig.getExperimentDuration()
				* MILLIS_IN_SECOND;
		lrClient.startLoad(lrConfig);
	}

	private long calculateActualRampUpDuration(final LGWorkloadConfig lrConfig) {

		final int rampUpInterval = lrConfig.getRampUpIntervalLength();
		final int rampUpUsersPerInterval = lrConfig.getRampUpUsersPerInterval();
		final int numUsers = lrConfig.getNumUsers();

		return ((numUsers / rampUpUsersPerInterval) - ((numUsers
				% rampUpUsersPerInterval == 0) ? 1 : 0))
				* rampUpInterval;
	}

	@Override
	public void waitForFinishedLoad() throws WorkloadException {
		lrClient.waitForFinishedLoad();
		LOGGER.info("Load generation finished.");
	}

	private LGWorkloadConfig createLRConfig(final LoadConfig loadConfig) {
		final LGWorkloadConfig lrConfig = new LGWorkloadConfig();

		lrConfig.setCoolDownIntervalLength(loadConfig
				.getCoolDownIntervalLength());
		lrConfig.setCoolDownUsersPerInterval(loadConfig
				.getCoolDownUsersPerInterval());
		lrConfig.setExperimentDuration(loadConfig.getExperimentDuration());
		lrConfig.setNumUsers(loadConfig.getNumUsers());
		lrConfig.setRampUpIntervalLength(loadConfig.getRampUpIntervalLength());
		lrConfig.setRampUpUsersPerInterval(loadConfig
				.getRampUpUsersPerInterval());

		lrConfig.setLoadGeneratorPath(LpeStringUtils.getPropertyOrFail(
				getProperties(), LRConfigKeys.CONTROLLER_EXE, null));

		lrConfig.setResultPath(LpeStringUtils.getPropertyOrFail(
				getProperties(), LRConfigKeys.RESULT_DIR, null));
		lrConfig.setScenarioPath(LpeStringUtils.getPropertyOrFail(
				getProperties(), LRConfigKeys.SCENARIO_FILE, null));
		lrConfig.setSchedulingMode(SchedulingMode.valueOf(LpeStringUtils
				.getPropertyOrFail(getProperties(),
						LRConfigKeys.EXPERIMENT_SCHEDULING_MODE,
						SchedulingMode.dynamicScheduling.toString())));
		lrConfig.setvUserInitMode(VUserInitializationMode
				.valueOf(LpeStringUtils.getPropertyOrFail(getProperties(),
						LRConfigKeys.EXPERIMENT_USER_INIT_MODE,
						VUserInitializationMode.beforeRunning.toString())));

		return lrConfig;
	}

	@Override
	public void waitForWarmupPhaseTermination() throws WorkloadException {
		while (System.currentTimeMillis() < experimentStartTime
				+ rampUpDuration) {
			try {
				Thread.sleep(POLLING_INTERVAL);
			} catch (final InterruptedException e) {
				throw new WorkloadException(e);
			}
		}

	}

	@Override
	public void waitForExperimentPhaseTermination() throws WorkloadException {
		while (System.currentTimeMillis() < experimentStartTime
				+ rampUpDuration + experimentDuration) {
			try {
				Thread.sleep(POLLING_INTERVAL);
			} catch (final InterruptedException e) {
				throw new WorkloadException(e);
			}
		}

	}

}
