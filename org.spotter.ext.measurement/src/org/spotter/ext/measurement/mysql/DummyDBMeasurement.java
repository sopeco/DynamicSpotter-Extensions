package org.spotter.ext.measurement.mysql;

import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.Future;

import javax.ws.rs.core.MediaType;

import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.AbstractRecord;
import org.aim.api.measurement.MeasurementData;
import org.aim.api.measurement.collector.AbstractDataSource;
import org.aim.api.measurement.collector.CollectorFactory;
import org.aim.artifacts.measurement.collector.FileDataSource;
import org.aim.artifacts.measurement.collector.MemoryDataSource;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.system.LpeSystemUtils;
import org.lpe.common.util.web.LpeWebUtils;
import org.spotter.core.measurement.AbstractMeasurementAdapter;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class DummyDBMeasurement extends AbstractMeasurementAdapter implements Runnable {

	public static Integer instanceId = 1;
	private AbstractDataSource dataSource;
	private Future<?> measurementTask;

	private boolean running;

	private long delay;
	private String host;
	private String port;
	protected static final long DEFAULT_DELAY = 500;

	private Client client;
	private WebResource webResource;

	public DummyDBMeasurement(IExtension<?> provider) {
		super(provider);
		client = LpeWebUtils.getWebClient();
		client.setConnectTimeout(1000 * 60 * 60);
		client.setReadTimeout(1000 * 60 * 60);

	}

	@Override
	public void enableMonitoring() throws MeasurementException {
		measurementTask = LpeSystemUtils.submitTask(this);
	}

	@Override
	public void disableMonitoring() throws MeasurementException {
		try {
			running = false;
			measurementTask.get();

		} catch (Exception e) {
			throw new MeasurementException(e);
		}
	}

	@Override
	public MeasurementData getMeasurementData() throws MeasurementException {
		return dataSource.read();
	}

	@Override
	public void pipeToOutputStream(OutputStream oStream) throws MeasurementException {
		try {
			dataSource.pipeToOutputStream(oStream);
		} catch (MeasurementException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void initialize() throws MeasurementException {
		if (getProperties().containsKey(DummyDBMeasurementExtension.SAMPLING_DELAY)) {
			delay = Long.valueOf(getProperties().getProperty(DummyDBMeasurementExtension.SAMPLING_DELAY));
		} else {
			delay = DEFAULT_DELAY;
		}

		if (getProperties().containsKey(DummyDBMeasurementExtension.HOST)) {
			host = getProperties().getProperty(DummyDBMeasurementExtension.HOST);
		} else {
			throw new RuntimeException("Host has not been specified!");
		}

		if (getProperties().containsKey(DummyDBMeasurementExtension.PORT)) {
			port = getProperties().getProperty(DummyDBMeasurementExtension.PORT);
		} else {
			throw new RuntimeException("Port has not been specified!");
		}

		webResource = client.resource("http://" + host + ":" + port + "/").path("dummyDB").path("getStatistics");
		Properties collectorProperties = GlobalConfiguration.getInstance().getProperties();
		synchronized (instanceId) {
			collectorProperties.setProperty(FileDataSource.ADDITIONAL_FILE_PREFIX_KEY, "DummyDBSampler-" + instanceId);
			instanceId++;
		}

		dataSource = CollectorFactory.createDataSource(MemoryDataSource.class.getName(),
				collectorProperties);

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
				sampleStatistics();
				try {
					Thread.sleep(delay);
				} catch (InterruptedException ie) {
					running = false;
				}
			}

			dataSource.disable();
		} catch (MeasurementException e) {
			throw new RuntimeException(e);
		}
	}

	private void sampleStatistics() {
		String recStr = webResource.accept(MediaType.APPLICATION_JSON).get(String.class);
		dataSource.newRecord(AbstractRecord.fromString(recStr));
	}

	@Override
	public void storeReport(String path) throws MeasurementException {
		// nothing to do here
	}

}
