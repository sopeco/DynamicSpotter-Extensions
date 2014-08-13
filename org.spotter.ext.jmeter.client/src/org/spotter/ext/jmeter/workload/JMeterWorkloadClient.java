/**
 * Copyright 2014 SAP AG
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.spotter.ext.jmeter.workload;

import java.io.IOException;

import org.lpe.common.extension.IExtension;
import org.lpe.common.jmeter.JMeterWrapper;
import org.lpe.common.jmeter.config.JMeterWorkloadConfig;
import org.lpe.common.util.LpeStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.jmeter.JMeterConfigKeys;

/**
 * The client to communicate with the JMeter server.
 */
public class JMeterWorkloadClient extends AbstractWorkloadAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(JMeterWorkloadClient.class);

	private static final int POLLING_INTERVAL = 500;

	private static final int MILLIS_IN_SECOND = 1000;

	private JMeterWrapper jmClient;

	private long experimentStartTime;

	private long rampUpDuration;

	private long experimentDuration;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public JMeterWorkloadClient(IExtension<?> provider) {
		super(provider);
	}

	@Override
	public void initialize() throws WorkloadException {
		jmClient = JMeterWrapper.getInstance();
	}

	@Override
	public void startLoad(LoadConfig loadConfig) throws WorkloadException {

		JMeterWorkloadConfig jMeterConfig = createJMeterConfig(loadConfig);

		LOGGER.info("Triggered load with {} users ...", jMeterConfig.getNumUsers());
		experimentStartTime = System.currentTimeMillis();
		rampUpDuration = calculateActualRampUpDuration(jMeterConfig);
		experimentDuration = jMeterConfig.getExperimentDuration() * MILLIS_IN_SECOND;

		try {
			jmClient.startLoadTest(jMeterConfig);
		} catch (IOException e) {
			throw new WorkloadException(e);
		}
	}

	/**
	 * Calculaters the estimated time for the ramp up phase for JMeter.
	 * 
	 * @param jMeterConfig
	 *            the {@link JMeterWorkloadConfig}
	 * @return the time
	 */
	private long calculateActualRampUpDuration(JMeterWorkloadConfig jMeterConfig) {
		int rampUpInterval = (int) jMeterConfig.getRampUpInterval();
		int rampUpUsersPerInterval = (int) jMeterConfig.getRampUpNumUsersPerInterval();
		int numUsers = jMeterConfig.getNumUsers();

		return ((numUsers / rampUpUsersPerInterval) - ((numUsers % rampUpUsersPerInterval == 0) ? 1 : 0))
				* rampUpInterval;
	}

	@Override
	public void waitForFinishedLoad() throws WorkloadException {
		try {
			jmClient.waitForLoadTestFinish();
		} catch (InterruptedException e) {
			throw new WorkloadException(e);
		}
		LOGGER.info("Load generation finished.");
	}

	private JMeterWorkloadConfig createJMeterConfig(LoadConfig loadConfig) {
		JMeterWorkloadConfig jMeterConfig = new JMeterWorkloadConfig();

		// required properties
		jMeterConfig.setExperimentDuration(loadConfig.getExperimentDuration());

		jMeterConfig.setNumUsers(loadConfig.getNumUsers());

		jMeterConfig.setRampUpInterval(loadConfig.getRampUpIntervalLength());

		jMeterConfig.setRampUpNumUsersPerInterval(loadConfig.getRampUpUsersPerInterval());

		jMeterConfig.setCoolDownInterval(loadConfig.getCoolDownIntervalLength());

		jMeterConfig.setCoolDownNumUsersPerInterval(loadConfig.getCoolDownUsersPerInterval());

		jMeterConfig.setPathToJMeterBinFolder(LpeStringUtils.getPropertyOrFail(getProperties(),
				JMeterConfigKeys.JMETER_HOME, null));

		jMeterConfig.setPathToScript(LpeStringUtils.getPropertyOrFail(getProperties(), JMeterConfigKeys.SCENARIO_FILE,
				null));

		jMeterConfig.setThinkTimeMaximum(Integer.parseInt(LpeStringUtils.getPropertyOrFail(getProperties(),
				JMeterConfigKeys.THINK_TIME_MIN, null)));

		jMeterConfig.setThinkTimeMinimum(Integer.parseInt(LpeStringUtils.getPropertyOrFail(getProperties(),
				JMeterConfigKeys.THINK_TIME_MAX, null)));

		// optional properties
		boolean sampleInFile = Boolean.parseBoolean(getProperties().getProperty(JMeterConfigKeys.SAMPLING_FLAG));

		if (sampleInFile) {
			jMeterConfig.setSamplingFileFlag(true);
			// fail when there is no property set for the sampling file
			jMeterConfig.setPathToSamplingFile(LpeStringUtils.getPropertyOrFail(getProperties(),
					JMeterConfigKeys.SAMPLING_FILE, null));
		}

		boolean createLogFile = Boolean.parseBoolean(getProperties().getProperty(JMeterConfigKeys.LOG_FILE_FLAG));

		if (createLogFile) {
			jMeterConfig.setCreateLogFlag(true);
			jMeterConfig.setLogFilePrefix(getProperties().getProperty(JMeterConfigKeys.LOG_FILE_PREFIX));
		}

		return jMeterConfig;
	}

	@Override
	public void waitForWarmupPhaseTermination() throws WorkloadException {
		while (System.currentTimeMillis() < experimentStartTime + rampUpDuration) {
			try {
				Thread.sleep(POLLING_INTERVAL);
			} catch (InterruptedException e) {
				throw new WorkloadException(e);
			}
		}

	}

	@Override
	public void waitForExperimentPhaseTermination() throws WorkloadException {
		while (System.currentTimeMillis() < experimentStartTime + rampUpDuration + experimentDuration) {
			try {
				Thread.sleep(POLLING_INTERVAL);
			} catch (InterruptedException e) {
				throw new WorkloadException(e);
			}
		}

	}

}
