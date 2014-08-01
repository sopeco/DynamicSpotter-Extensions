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
package org.spotter.ext.workload.simple;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;
import org.lpe.common.config.GlobalConfiguration;
import org.spotter.core.workload.IWorkloadAdapter;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.configuration.ConfigKeys;

public class SimpleWorkloadDriverTest {
	@BeforeClass
	public static void initGlobalConfig() {
		GlobalConfiguration.initialize(new Properties());
	}

	@Test
	public void testLoadExecution() throws URISyntaxException, WorkloadException {
		URL url = SimpleWorkloadDriverTest.class.getResource("/");
		// URL url =
		// SimpleWorkloadDriverTest.class.getResource(SimpleVUser.class.getName());
		String vuserPath = url.toURI().getPath();

		Properties workloadProperties = new Properties();
		workloadProperties.setProperty(IWorkloadAdapter.NUMBER_CURRENT_USERS, String.valueOf(1));
		workloadProperties.setProperty(SimpleWorkloadDriver.USER_SCRIPT_PATH, vuserPath);
		workloadProperties.setProperty(SimpleWorkloadDriver.USER_SCRIPT_CLASS_NAME, SimpleVUser.class.getName());
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_DURATION, String.valueOf(1));
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_RAMP_UP_INTERVAL_LENGTH, String.valueOf(1));
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_RAMP_UP_NUM_USERS_PER_INTERVAL, String.valueOf(10));
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_COOL_DOWN_INTERVAL_LENGTH, String.valueOf(1));
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_COOL_DOWN_NUM_USERS_PER_INTERVAL, String.valueOf(20));

		SimpleWorkloadDriver swlDriver = new SimpleWorkloadDriver(null);
		swlDriver.startLoad(workloadProperties);
		swlDriver.waitForWarmupPhaseTermination();
		swlDriver.waitForExperimentPhaseTermination();
		swlDriver.waitForFinishedLoad();
	}

	@Test(expected = WorkloadException.class)
	public void testInvalidExecution() throws URISyntaxException, WorkloadException {
		URL url = SimpleWorkloadDriverTest.class.getResource("/");
		// URL url =
		// SimpleWorkloadDriverTest.class.getResource(SimpleVUser.class.getName());
		String vuserPath = url.toURI().getPath();

		Properties workloadProperties = new Properties();
		workloadProperties.setProperty(IWorkloadAdapter.NUMBER_CURRENT_USERS, String.valueOf(1));
		workloadProperties.setProperty(SimpleWorkloadDriver.USER_SCRIPT_PATH, vuserPath);
		workloadProperties.setProperty(SimpleWorkloadDriver.USER_SCRIPT_CLASS_NAME, SimpleVUser.class.getName()+"INVALID");
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_DURATION, String.valueOf(1));
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_RAMP_UP_INTERVAL_LENGTH, String.valueOf(1));
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_RAMP_UP_NUM_USERS_PER_INTERVAL, String.valueOf(10));
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_COOL_DOWN_INTERVAL_LENGTH, String.valueOf(1));
		workloadProperties.setProperty(ConfigKeys.EXPERIMENT_COOL_DOWN_NUM_USERS_PER_INTERVAL, String.valueOf(20));

		SimpleWorkloadDriver swlDriver = new SimpleWorkloadDriver(null);
		swlDriver.startLoad(workloadProperties);
		swlDriver.waitForWarmupPhaseTermination();
		swlDriver.waitForExperimentPhaseTermination();
		swlDriver.waitForFinishedLoad();
	}
}
