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
package org.spotter.ext.measurement.resourcemonitoring;

import java.io.OutputStream;
import java.util.Properties;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.artifacts.sampler.CPUSampler;
import org.aim.artifacts.sampler.NetworkIOSampler;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.aim.resourcemonitoring.ResourceMonitoringClient;
import org.lpe.common.extension.IExtension;
import org.spotter.core.measurement.AbstractMeasurementAdapter;

/**
 * REST client for the resource monitoring service.
 * 
 * @author Alexander Wert
 * 
 */
public class ResourceMonitoringAdapter extends AbstractMeasurementAdapter {

	public static final String SAMPLING_DELAY = "org.spotter.sampling.delay";

	private ResourceMonitoringClient client;

	private long samplingDelay;
	protected static final long DEFAULT_DELAY = 1000;

	/**
	 * Construcotr.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public ResourceMonitoringAdapter(IExtension<?> provider) {
		super(provider);

	}

	@Override
	public void enableMonitoring() throws MeasurementException {

		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newSampling(CPUSampler.class.getName(), samplingDelay);
		idBuilder.newSampling(NetworkIOSampler.class.getName(), samplingDelay);

		client.enableMonitoring(idBuilder.build());

	}

	@Override
	public void disableMonitoring() throws MeasurementException {
		client.disableMonitoring();

	}

	@Override
	public MeasurementData getMeasurementData() throws MeasurementException {

		MeasurementData measurementData = client.getMeasurementData();

		return measurementData;
	}

	@Override
	public long getCurrentTime() {
		return client.getCurrentTime();
	}

	@Override
	public void initialize() throws MeasurementException {
		if (client == null) {
			client = new ResourceMonitoringClient(getHost(), getPort());

			if (!client.testConnection()) {
				throw new MeasurementException("Connection to measurement satellite could not be established!");
			}

			Properties measurementProperties = getProperties();

			if (measurementProperties.containsKey(SAMPLING_DELAY)) {
				samplingDelay = Long.valueOf(measurementProperties.getProperty(SAMPLING_DELAY));
			} else {
				samplingDelay = DEFAULT_DELAY;
			}
		}
	}

	@Override
	public void pipeToOutputStream(OutputStream oStream) throws MeasurementException {
		client.pipeToOutputStream(oStream);

	}

	@Override
	public void storeReport(String path) throws MeasurementException {
		// nothing to do here.
	}

	@Override
	public void prepareMonitoring(InstrumentationDescription monitoringDescription) throws MeasurementException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resetMonitoring() throws MeasurementException {
		// TODO Auto-generated method stub
		
	}

}
