package org.spotter.ext.measurement.database;

import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Future;

import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.MeasurementData;
import org.aim.api.measurement.collector.AbstractDataSource;
import org.aim.api.measurement.collector.CollectorFactory;
import org.aim.artifacts.measurement.collector.FileDataSource;
import org.aim.artifacts.records.DBStatisticsRecrod;
import org.aim.description.InstrumentationDescription;
import org.aim.description.sampling.SamplingDescription;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.system.LpeSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.measurement.AbstractMeasurementAdapter;

public class HDBMeasurement extends AbstractMeasurementAdapter implements Runnable {
	private static final Logger LOGGER = LoggerFactory.getLogger(HDBMeasurement.class);
	public static Integer instanceId = 1;
	private AbstractDataSource dataSource;
	private String host;
	private String port;
	private String username;
	private String password;
	private Long delay;
	private Connection connection;
	private PreparedStatement sqlStatement;
	private boolean running;
	private boolean samplerActivated = false;
	private Future<?> measurementTask;
	private static final String SQL_QUERY = "SELECT * FROM M_LOCK_WAITS_STATISTICS WHERE PORT LIKE '%03' AND LOCK_TYPE='TABLE';";

	public HDBMeasurement(IExtension<?> provider) {
		super(provider);
	}

	@Override
	public void enableMonitoring() throws MeasurementException {
		if (samplerActivated) {
			try {
				sqlStatement = getConnection().prepareStatement(SQL_QUERY);
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
					sqlStatement = null;
				}
				if (connection != null) {
					connection.close();
					connection = null;
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
		} else {
			return new MeasurementData();
		}
	}

	@Override
	public void pipeToOutputStream(OutputStream oStream) throws MeasurementException {
		if (samplerActivated) {
			try {
				dataSource.pipeToOutputStream(oStream);
			} catch (MeasurementException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void initialize() throws MeasurementException {
		host = getProperties().getProperty(HDBMeasurementExtension.HOST_KEY, "localhost");
		port = getProperties().getProperty(HDBMeasurementExtension.PORT_KEY, "11111");
		username = getProperties().getProperty(HDBMeasurementExtension.USER_NAME_KEY, "");
		password = getProperties().getProperty(HDBMeasurementExtension.PASSWORD_KEY, "");

		Properties collectorProperties = new Properties();
		synchronized (instanceId) {
			collectorProperties.setProperty(FileDataSource.ADDITIONAL_FILE_PREFIX_KEY, "HDBSampler-" + instanceId);
			instanceId++;
		}

		dataSource = CollectorFactory.createDataSource(FileDataSource.class.getName(), collectorProperties);
	}

	@Override
	public long getCurrentTime() {
		return System.currentTimeMillis();
	}

	@Override
	public void storeReport(String path) throws MeasurementException {
		// TODO Auto-generated method stub

	}

	@Override
	public void run() {
		running = true;
		try {
			dataSource.enable();
			long counter = 0;
			while (running) {
				sampleLockStatistics(counter);
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

	private void sampleLockStatistics(long ownNumQueries) {

		try {
			ResultSet resultSet = sqlStatement.executeQuery();
			long numQueueries = -1;
			long numLockWaits = 0;
			long lockTime = 0;
			resultSet.next();
			numLockWaits = resultSet.getLong("TOTAL_LOCK_WAITS");
			lockTime = resultSet.getLong("TOTAL_LOCK_WAIT_TIME");
			resultSet.close();
			DBStatisticsRecrod record = new DBStatisticsRecrod();
			record.setTimeStamp(System.currentTimeMillis());
			record.setNumQueueries(numQueueries);
			record.setProcessId(host + ":" + port);
			record.setNumLockWaits(numLockWaits);
			record.setLockTime(lockTime);
			dataSource.newRecord(record);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}

	protected Connection getConnection() {
		if (connection != null) {
			return connection;
		}
		Properties props = new Properties();
		props.setProperty("user", username);
		props.setProperty("password", password);
		String connectionString = "jdbc:sap://" + host + ":" + port;
		try {
			Class.forName("com.sap.db.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			LOGGER.error(e.getMessage());
			return null;
		}
		try {
			connection = DriverManager.getConnection(connectionString, props);
		} catch (SQLException e) {
			LOGGER.error(e.getMessage());
			return null;
		}
		return connection;
	}

	public boolean testConnection() {
		return getConnection() != null;
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
