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
package org.spotter.ext.measurement.database;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Future;

import javax.net.ssl.HostnameVerifier;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.description.sampling.SamplingDescription;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.collector.AbstractDataSource;
import org.aim.api.measurement.collector.CollectorFactory;
import org.aim.artifacts.measurement.collector.FileDataSource;
import org.aim.artifacts.records.DBStatisticsRecrod;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.system.LpeSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.measurement.AbstractMeasurementAdapter;

/**
 * Measurement adapter for sampling status of a Database Server. TODO: this
 * class is specific
 * 
 * @author Alexander Wert
 * 
 */
public class DBMSMeasurement extends AbstractMeasurementAdapter implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(DBMSMeasurement.class);
	
	public static Integer instanceId = 1;
	private AbstractDataSource dataSource;
	private Future<?> measurementTask;

	private Connection jdbcConnection;
	private PreparedStatement sqlStatement;
	private boolean running;
	private boolean samplerActivated = false;
	private long delay;
	private String mySQLHost;
	private String mySQLPort;
	private String mySQLUser;
	private String mySQLPW;
	private String mySQLdatabase;
	protected static final long DEFAULT_DELAY = 500;

	/**
	 * Query string for Database MS status. TODO: it is specific yet,
	 * externalize
	 */
	private static final String SQL_QUERY = "SHOW STATUS WHERE Variable_name like "
			+ "'Innodb_row_lock_waits' OR Variable_name like 'Queries' OR "
			+ "Variable_name like 'Innodb_row_lock_time';";

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public DBMSMeasurement(IExtension<?> provider) {
		super(provider);
	}

	@Override
	public void enableMonitoring() throws MeasurementException {
		if (samplerActivated) {
			try {
				String dbConnectionString = "jdbc:mysql://" + mySQLHost + ":"+ mySQLPort + "/" + mySQLdatabase;
				jdbcConnection = DriverManager.getConnection(dbConnectionString,mySQLUser,mySQLPW);
				sqlStatement = jdbcConnection.prepareStatement(SQL_QUERY);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			measurementTask = LpeSystemUtils.submitTask(this);
		}

	}

	@Override
	public void disableMonitoring() throws MeasurementException {
		if (samplerActivated) {
			try {
				running = false;
				measurementTask.get();
				if (sqlStatement != null) {
					sqlStatement.close();
				}
				if (jdbcConnection != null) {
					jdbcConnection.close();
				}

			} catch (Exception e) {
				throw new MeasurementException(e);
			}
		}
	}

	@Override
	public MeasurementData getMeasurementData() throws MeasurementException {
		if (samplerActivated) {
			return dataSource.read();
		}
		return new MeasurementData();
	}

	@Override
	public void pipeToOutputStream(OutputStream oStream) throws MeasurementException {
		if (samplerActivated) {
			dataSource.pipeToOutputStream(oStream);
		} else {
			try {
				oStream.close();
			} catch (IOException e) {
				throw new MeasurementException(e);
			}
		}
	}

	@Override
	public void initialize() throws MeasurementException {

		mySQLHost = getProperties().getProperty(DBMSMeasurementExtension.HOST);
		mySQLPort = getProperties().getProperty(DBMSMeasurementExtension.PORT);
		mySQLUser = getProperties().getProperty(DBMSMeasurementExtension.USER);
		mySQLPW= getProperties().getProperty(DBMSMeasurementExtension.PASSWORD);
		mySQLdatabase = getProperties().getProperty(DBMSMeasurementExtension.DATABASE);

		Properties collectorProperties = GlobalConfiguration.getInstance().getProperties();
		synchronized (instanceId) {
			collectorProperties.setProperty(FileDataSource.ADDITIONAL_FILE_PREFIX_KEY, "MySQLSampler-" + instanceId);
			instanceId++;
		}

		dataSource = CollectorFactory.createDataSource(FileDataSource.class.getName(), collectorProperties);
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
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
			long counter = 0;
			while (running) {
				sampleMySQLStatistics(counter);
				counter++;
				try {
					Thread.sleep(delay);
				} catch (InterruptedException ie) {
					LOGGER.debug("Sleeptime interrupted.");
					running = false;
				}
			}

			dataSource.disable();
		} catch (MeasurementException e) {
			throw new RuntimeException(e);
		}
	}

	private void sampleMySQLStatistics(long ownNumQueries) {

		try {
			ResultSet resultSet = sqlStatement.executeQuery();
			String name = "";
			long numQueueries = 0;
			long numLockWaits = 0;
			long lockTime = 0;
			while (resultSet.next()) {
				name = resultSet.getString("Variable_name");

				if ("Queries".equals(name)) {
					numQueueries = resultSet.getLong("Value") - ownNumQueries;
				}

				if ("Innodb_row_lock_waits".equals(name)) {
					numLockWaits = resultSet.getLong("Value");
				}

				if ("Innodb_row_lock_time".equals(name)) {
					lockTime = resultSet.getLong("Value");
				}

				continue;
			}
			resultSet.close();
			DBStatisticsRecrod record = new DBStatisticsRecrod();
			record.setTimeStamp(System.currentTimeMillis());
			record.setNumQueueries(numQueueries);
			record.setProcessId(mySQLHost);
			record.setNumLockWaits(numLockWaits);
			record.setLockTime(lockTime);
			dataSource.newRecord(record);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public void storeReport(String path) throws MeasurementException {
		// nothing to do here
	}

	@Override
	public void prepareMonitoring(InstrumentationDescription monitoringDescription) throws MeasurementException {
		for (SamplingDescription sDescr : monitoringDescription.getSamplingDescriptions()) {
			if (sDescr.getResourceName().equals(SamplingDescription.SAMPLER_DATABASE_STATISTICS)) {
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
