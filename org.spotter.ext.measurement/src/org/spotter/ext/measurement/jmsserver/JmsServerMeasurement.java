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
package org.spotter.ext.measurement.jmsserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.description.sampling.SamplingDescription;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.collector.AbstractDataSource;
import org.aim.api.measurement.collector.CollectorFactory;
import org.aim.artifacts.measurement.collector.FileDataSource;
import org.aim.artifacts.records.JmsServerRecord;
import org.apache.activemq.broker.jmx.BrokerViewMBean;
import org.apache.activemq.broker.jmx.QueueViewMBean;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.system.LpeSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.measurement.AbstractMeasurementAdapter;

/**
 * Measurement adapter for sampling status of a JMS Server.
 * 
 * @author Alexander Wert
 * 
 */
public class JmsServerMeasurement extends AbstractMeasurementAdapter implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(JmsServerMeasurement.class);
	public static final String DESTINATION_NAME = "org.spotter.measurement.jmsserver.DestinationName";
	public static final String ACTIVE_MQJMX_URL = "org.spotter.measurement.jmsserver.ActiveMQJMXUrl";

	private AbstractDataSource dataSource;
	private Future<?> measurementTask;

	private List<QueueViewMBean> queueMbeans;
	private BrokerViewMBean mbean;
	private boolean running;
	private boolean samplerActivated = false;
	private boolean messagingServerAvailable = false;
	private long delay;
	protected static final long DEFAULT_DELAY = 500;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public JmsServerMeasurement(final IExtension provider) {
		super(provider);
	}

	@Override
	public void enableMonitoring() throws MeasurementException {
		if (samplerActivated && messagingServerAvailable) {
			resetActiveMQStatistics();
			measurementTask = LpeSystemUtils.submitTask(this);
		}

	}

	@Override
	public void disableMonitoring() throws MeasurementException {
		if (samplerActivated && messagingServerAvailable) {
			try {
				running = false;
				measurementTask.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new MeasurementException(e);
			}
		}

	}

	private void resetActiveMQStatistics() {
		try {
			LOGGER.debug("purge and reset ActiveMQ server");
			for (final QueueViewMBean queueMbean : queueMbeans) {
				// queueMbean.purge();
				queueMbean.resetStatistics();
			}
			mbean.resetStatistics();

		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public MeasurementData getMeasurementData() throws MeasurementException {
		if (samplerActivated && messagingServerAvailable) {
			return dataSource.read();
		} else {
			return new MeasurementData();
		}

	}

	@Override
	public void pipeToOutputStream(final OutputStream oStream) throws MeasurementException {
		if (samplerActivated && messagingServerAvailable) {
			dataSource.pipeToOutputStream(oStream);
		} else {
			try {
				oStream.close();
			} catch (final IOException e) {
				throw new MeasurementException(e);
			}
		}

	}

	@Override
	public void initialize() throws MeasurementException {

		final Properties collectorProperties = GlobalConfiguration.getInstance().getProperties();
		collectorProperties.setProperty(FileDataSource.ADDITIONAL_FILE_PREFIX_KEY, "JMSServerSampler");

		dataSource = CollectorFactory.createDataSource(FileDataSource.class.getName(), collectorProperties);

		try {
			LOGGER.debug("Connect to JMX ActiveMQ server");
			final String activeMqJMX = getProperties().getProperty(ACTIVE_MQJMX_URL);

			final JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(activeMqJMX));
			connector.connect();

			final MBeanServerConnection connection = connector.getMBeanServerConnection();

			final ObjectName mbeanName = new ObjectName("org.apache.activemq:type=Broker,brokerName=myBroker");
			mbean = MBeanServerInvocationHandler.newProxyInstance(connection, mbeanName, BrokerViewMBean.class, true);

			queueMbeans = new ArrayList<>();
			for (final ObjectName queueName : mbean.getQueues()) {
				final QueueViewMBean tempQueueMbean = MBeanServerInvocationHandler.newProxyInstance(
						connection, queueName, QueueViewMBean.class, true);
				queueMbeans.add(tempQueueMbean);
			}
			messagingServerAvailable = true;
		} catch (final Exception e) {
			LOGGER.error("Messaging Server not available!");
			messagingServerAvailable = false;
		}
	}

	@Override
	public long getCurrentTime() {
		return System.currentTimeMillis();
	}

	@Override
	public void run() {
		running = true;
		try {
			dataSource.enable();

			while (running) {
				sampleJmsServerStatistics();

				try {
					Thread.sleep(delay);
				} catch (final InterruptedException ie) {
					LOGGER.debug("Sleeptime interrupted.");
					running = false;
				}
			}

			dataSource.disable();
		} catch (final MeasurementException e) {

			throw new RuntimeException(e);
		}
	}

	/**
	 * Samples JMS Server status
	 */
	private void sampleJmsServerStatistics() {
		for (final QueueViewMBean tempBean : queueMbeans) {
			final JmsServerRecord record = new JmsServerRecord();
			record.setQueueName(tempBean.getName());
			record.setTimeStamp(System.currentTimeMillis());
			record.setAverageEnqueueTime(tempBean.getAverageEnqueueTime());
			record.setDequeueCount(tempBean.getDequeueCount());
			record.setDispatchCount(tempBean.getDispatchCount());
			record.setEnqueueCount(tempBean.getEnqueueCount());
			record.setMemoryPercentUsage(tempBean.getMemoryPercentUsage());
			record.setMemoryUsage(tempBean.getMemoryUsageByteCount());
			record.setQueueSize(tempBean.getQueueSize());
			record.setAvgMessageSize(mbean.getAverageMessageSize());
			dataSource.newRecord(record);
		}
	}

	@Override
	public void storeReport(final String path) throws MeasurementException {
		// nothing to do here.
	}

	@Override
	public void prepareMonitoring(final InstrumentationDescription monitoringDescription) throws MeasurementException {
		for (final SamplingDescription sDescr : monitoringDescription.getSamplingDescriptions()) {
			if (sDescr.getResourceName().equals(SamplingDescription.SAMPLER_MESSAGING_STATISTICS)) {
				samplerActivated = true;
				delay = sDescr.getDelay();
				break;
			}
		}

	}

	@Override
	public void resetMonitoring() throws MeasurementException {
		samplerActivated = false;

	}
}
