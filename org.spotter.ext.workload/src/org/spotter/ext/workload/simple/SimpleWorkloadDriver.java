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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.system.LpeSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;

/**
 * Generates a simple closed workload.
 * 
 * @author Alexander Wert
 */
public class SimpleWorkloadDriver extends AbstractWorkloadAdapter {

	private static final long _1000L = 1000L;

	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleWorkloadDriver.class);

	/**
	 * The path configuration key to the user script path.
	 */
	public static final String USER_SCRIPT_PATH = "org.spotter.workload.simple.userScriptPath";

	/**
	 * The configuration key to the user script class name.
	 */
	public static final String USER_SCRIPT_CLASS_NAME = "org.spotter.workload.simple.userScriptClassName";

	/**
	 * The monitor helps to enable the possible for others threads, to passivly
	 * wait (warmUpMonitor.wait()) for this thread till the warm-up phase is
	 * finished. Otherwise, buzy waiting needs to be used.
	 */
	private final Object warmUpMonitor = new Object();

	/**
	 * The monitor helps to enable the possible for others threads, to passivly
	 * wait (experimentMonitor.wait()) for this thread till the experimentation
	 * phase is finished. Otherwise, buzy waiting needs to be used.
	 */
	private final Object experimentMonitor = new Object();

	/**
	 * True, when the warm-up / ramp-up phase for the experiment has been
	 * finished.
	 */
	private boolean warmupPhaseFinished;

	/**
	 * True, when the warm-up phase + experiment phase has been finished.
	 */
	private boolean experimentPhaseFinished;

	private URLClassLoader urlClassLoader;

	private Future<?> workloadGenerationJob;

	/**
	 * The number of (virtual) users currently in the system. This variable
	 * should be accessed in a syncronized way.
	 */
	private int numActiveUsers = 0;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public SimpleWorkloadDriver(final IExtension provider) {
		super(provider);
	}

	@Override
	public void startLoad(final LoadConfig loadConfig) throws WorkloadException {

		// reset current variables
		warmupPhaseFinished = false;
		experimentPhaseFinished = false;
		numActiveUsers = 0;
		final Object wlDriver = this;
		final Runnable task = new Runnable() {

			@Override
			public void run() {
				try {
					// load the global configuration and overwrite local
					// configration properties
					getProperties().putAll(GlobalConfiguration.getInstance().getProperties());

					checkProperties(getProperties());
					final int numberUsers = loadConfig.getNumUsers();
					LOGGER.info("starting " + numberUsers + " vUsers ...");
					final File userScriptFile = new File(getProperties().getProperty(USER_SCRIPT_PATH));
					final String userScriptClassNAme = getProperties().getProperty(USER_SCRIPT_CLASS_NAME);

					final long experimentDuration = loadConfig.getExperimentDuration() * _1000L; // [ms]

					// load one virutal user
					Class<?> vUserClass;

					vUserClass = loadVUserScript(userScriptFile, userScriptClassNAme);

					// we need to keep track for the offset for the cooldown of
					// the
					// virtual user, we
					// are executing every loop
					int timeOffsetMultiplicatorCoolDown = 0;

					for (int i = 0; i < numberUsers; i++) {

						if ((i + 1) % loadConfig.getCoolDownUsersPerInterval() == 0) {
							timeOffsetMultiplicatorCoolDown++;
						}

						startVUser(vUserClass, loadConfig.getCoolDownIntervalLength() * timeOffsetMultiplicatorCoolDown);

						// We put "rampUpUsersPerInterval" into the system. When
						// we
						// have added all of them in one
						// interval, we set the WorkloadDriver to sleep.
						// Afterwards the loop is going to continue the next
						// users
						// for the next ramp up interval.
						if ((i + 1) % loadConfig.getRampUpUsersPerInterval() == 0) {
							sleep(loadConfig.getRampUpIntervalLength());
						}

					}

					// Some thread could be waiting for us, till the warm-up
					// phase
					// is finished. Notify them!
					synchronized (warmUpMonitor) {
						warmupPhaseFinished = true;
						warmUpMonitor.notifyAll();
					}

					// In the experimentation time, the users are just executing
					// their task. In this time
					// system bottlenecks might get visible
					sleep(experimentDuration);

					// Some thread could be waiting for us, till the experiment
					// phase is finished. Notify them!
					synchronized (experimentMonitor) {
						experimentPhaseFinished = true;
						experimentMonitor.notifyAll();
					}

					LOGGER.info("Simple load with " + numberUsers + " vUsers terminated");
				} catch (final Throwable e) {
					synchronized (warmUpMonitor) {
						warmupPhaseFinished = true;
						warmUpMonitor.notifyAll();
					}
					synchronized (experimentMonitor) {
						experimentPhaseFinished = true;
						experimentMonitor.notifyAll();
					}
					synchronized (wlDriver) {
						LOGGER.debug("notifying all...");
						wlDriver.notifyAll();
					}
					throw new RuntimeException(e);
				}
			}
		};

		workloadGenerationJob = LpeSystemUtils.submitTask(task);

	}

	private void sleep(final long delay) {
		try {
			Thread.sleep(delay);
		} catch (final InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 
	 * 
	 * @param vUserClass
	 *            the users class with all properties set
	 * @param coolDownDelay
	 */
	private void startVUser(final Class<?> vUserClass, final long coolDownDelay) {
		LpeSystemUtils.submitTask(new Runnable() {
			@Override
			public void run() {
				ISimpleVUser vUser;
				try {
					vUser = (ISimpleVUser) vUserClass.newInstance();
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}

				increaseNumActiveUsers();

				while (!experimentPhaseFinished) {
					vUser.executeIteration();
				}
				final long coolDownPhaseStart = System.currentTimeMillis();
				while ((System.currentTimeMillis() - coolDownPhaseStart) < coolDownDelay) {
					vUser.executeIteration();
				}
				decreaseNumActiveUsers();

			}
		});
	}

	private Class<?> loadVUserScript(final File userScriptFile, final String userScriptClassNAme) throws WorkloadException {
		URL url;
		final Class<?> vUserClass;
		try {
			url = userScriptFile.toURI().toURL();
			final URL[] urls = new URL[] { url };

			urlClassLoader = new URLClassLoader(urls, this.getClass().getClassLoader());

			vUserClass = urlClassLoader.loadClass(userScriptClassNAme);
		} catch (final MalformedURLException e) {
			throw new WorkloadException(e);
		} catch (final ClassNotFoundException e) {
			// Cannot include ClassNotFoundException because it is not parsable
			// as JSON string due to unrecognized field ("ex" instead of
			// "exception")
			throw new WorkloadException("Could not find specified class '" + e.getMessage() + "'");
		}
		return vUserClass;
	}

	/**
	 * Checks the passed properties, to contain values for the following keys:
	 * <ol>
	 * <li>{@link #USER_SCRIPT_PATH}</li>
	 * <li>{@link #USER_SCRIPT_CLASS_NAME}</li>
	 * <li>{@link #NUMBER_CURRENT_USERS}</li>
	 * </ol>
	 * 
	 * @param properties
	 *            the properties to check
	 */
	private void checkProperties(final Properties properties) {

		if (!properties.containsKey(USER_SCRIPT_PATH)) {
			throw new RuntimeException("User script file has not been specified!");
		}

		if (!properties.containsKey(USER_SCRIPT_CLASS_NAME)) {
			throw new RuntimeException("Class name for user script has not been specified");
		}

	}

	/**
	 * Synchronized method to incerease the number of the users in the system up
	 * by one (+1).
	 */
	private synchronized void increaseNumActiveUsers() {
		numActiveUsers++;
		notifyAll();
	}

	/**
	 * Synchronized method to decrease the number of the users in the system by
	 * one (-1).
	 */
	private synchronized void decreaseNumActiveUsers() {
		numActiveUsers--;

		notifyAll();
	}

	@Override
	public synchronized void waitForFinishedLoad() throws WorkloadException {
		LOGGER.debug("waiting for finished load ...");
		synchronized (this) {
			while (numActiveUsers > 0 || !warmupPhaseFinished || !experimentPhaseFinished) {
				LOGGER.debug("activeUsers: {}", numActiveUsers);
				LOGGER.debug("warmupPhaseFinished: {}", warmupPhaseFinished);
				LOGGER.debug("experimentPhaseFinished: {}", experimentPhaseFinished);
				try {
					this.wait();
				} catch (final InterruptedException e) {
					throw new WorkloadException(e);
				}
			}
		}
		LOGGER.debug("load finished");
		try {
			workloadGenerationJob.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new WorkloadException(e);
		}

	}

	@Override
	public void initialize() throws WorkloadException {
		// TODO Auto-generated method stub
	}

	@Override
	public void waitForWarmupPhaseTermination() throws WorkloadException {
		LOGGER.debug("waiting for warm up phase termination ...");
		synchronized (warmUpMonitor) {
			while (!warmupPhaseFinished) {
				try {
					warmUpMonitor.wait();
				} catch (final InterruptedException e) {
					throw new WorkloadException(e);
				}
			}
		}
		LOGGER.debug("warm-up phase terminated");

	}

	@Override
	public void waitForExperimentPhaseTermination() throws WorkloadException {
		LOGGER.debug("waiting for experiment phase termination ...");
		synchronized (experimentMonitor) {
			while (!experimentPhaseFinished) {
				try {
					LOGGER.debug("going to wait in: experiment phase termination ...");
					experimentMonitor.wait();
				} catch (final InterruptedException e) {
					throw new WorkloadException(e);
				}
			}
		}
		LOGGER.debug("experiment phase terminated");

	}

	@Override
	protected void finalize() throws Throwable {
		urlClassLoader.close();
	}

}
