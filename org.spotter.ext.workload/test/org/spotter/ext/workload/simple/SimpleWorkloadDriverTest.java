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
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;

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
		workloadProperties.setProperty(SimpleWorkloadDriver.USER_SCRIPT_PATH, vuserPath);
		workloadProperties.setProperty(SimpleWorkloadDriver.USER_SCRIPT_CLASS_NAME, SimpleVUser.class.getName());

		LoadConfig lConfig = new LoadConfig();
		lConfig.setNumUsers(1);
		lConfig.setRampUpIntervalLength(1);
		lConfig.setRampUpUsersPerInterval(10);
		lConfig.setCoolDownIntervalLength(1);
		lConfig.setCoolDownUsersPerInterval(20);
		lConfig.setExperimentDuration(1);

		SimpleWorkloadDriver swlDriver = new SimpleWorkloadDriver(null);
		swlDriver.setProperties(workloadProperties);
		swlDriver.startLoad(lConfig);
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
		workloadProperties.setProperty(SimpleWorkloadDriver.USER_SCRIPT_PATH, vuserPath);
		workloadProperties.setProperty(SimpleWorkloadDriver.USER_SCRIPT_CLASS_NAME, SimpleVUser.class.getName()
				+ "INVALID");

		LoadConfig lConfig = new LoadConfig();
		lConfig.setNumUsers(1);
		lConfig.setRampUpIntervalLength(1);
		lConfig.setRampUpUsersPerInterval(10);
		lConfig.setCoolDownIntervalLength(1);
		lConfig.setCoolDownUsersPerInterval(20);
		lConfig.setExperimentDuration(1);

		SimpleWorkloadDriver swlDriver = new SimpleWorkloadDriver(null);
		swlDriver.setProperties(workloadProperties);
		swlDriver.startLoad(lConfig);
		swlDriver.waitForWarmupPhaseTermination();
		swlDriver.waitForExperimentPhaseTermination();
		swlDriver.waitForFinishedLoad();
	}
}
