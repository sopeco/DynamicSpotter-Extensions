package org.spotter.ext.measurement.database;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.Future;

import javax.ws.rs.core.MediaType;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.description.sampling.SamplingDescription;
import org.aim.aiminterface.entities.measurements.AbstractRecord;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.collector.AbstractDataSource;
import org.aim.api.measurement.collector.CollectorFactory;
import org.aim.artifacts.measurement.collector.FileDataSource;
import org.aim.artifacts.measurement.collector.MemoryDataSource;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.system.LpeSystemUtils;
import org.lpe.common.util.web.LpeHTTPUtils;
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
	private boolean samplerActivated = false;
	protected static final long DEFAULT_DELAY = 500;

	private final Client client;
	private WebResource webResource;

	public DummyDBMeasurement(final IExtension provider) {
		super(provider);
		client = LpeHTTPUtils.getWebClient();
		client.setConnectTimeout(1000 * 60 * 60);
		client.setReadTimeout(1000 * 60 * 60);

	}

	@Override
	public void enableMonitoring() throws MeasurementException {
		if (samplerActivated) {
			measurementTask = LpeSystemUtils.submitTask(this);
		}

	}

	@Override
	public void disableMonitoring() throws MeasurementException {
		if (samplerActivated) {
			try {
				running = false;
				measurementTask.get();

			} catch (final Exception e) {
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
	public void pipeToOutputStream(final OutputStream oStream) throws MeasurementException {
		if (samplerActivated) {
				dataSource.pipeToOutputStream(oStream);
		}else{
			try {
				oStream.close();
			} catch (final IOException e) {
				throw new MeasurementException(e);
			}
		}
	}

	@Override
	public void initialize() throws MeasurementException {
		host = getHost();
		port = getPort();

		webResource = client.resource("http://" + host + ":" + port + "/").path("dummyDB").path("getStatistics");
		final Properties collectorProperties = GlobalConfiguration.getInstance().getProperties();
		synchronized (instanceId) {
			collectorProperties.setProperty(FileDataSource.ADDITIONAL_FILE_PREFIX_KEY, "DummyDBSampler-" + instanceId);
			instanceId++;
		}

		dataSource = CollectorFactory.createDataSource(MemoryDataSource.class.getName(), collectorProperties);

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
				} catch (final InterruptedException ie) {
					running = false;
				}
			}

			dataSource.disable();
		} catch (final MeasurementException e) {
			throw new RuntimeException(e);
		}
	}

	private void sampleStatistics() {
		final String recStr = webResource.accept(MediaType.APPLICATION_JSON).get(String.class);
		dataSource.newRecord(AbstractRecord.fromString(recStr));
	}

	@Override
	public void storeReport(final String path) throws MeasurementException {
		// nothing to do here
	}

	@Override
	public void prepareMonitoring(final InstrumentationDescription monitoringDescription) throws MeasurementException {
		for (final SamplingDescription sDescr : monitoringDescription.getSamplingDescriptions()) {
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
