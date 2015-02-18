package org.spotter.ext.measurement.database;

import javax.ws.rs.core.MediaType;

import org.lpe.common.util.web.LpeWebUtils;
import org.spotter.core.measurement.AbstractMeasurmentExtension;
import org.spotter.core.measurement.IMeasurementAdapter;

import com.sun.jersey.api.client.Client;

public class DummyDBMeasurementExtension extends AbstractMeasurmentExtension {


	@Override
	public String getName() {
		return "DummyDB Statistics Sampler";
	}

	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "DummyDB Sampling Measurement Satellite Adapter";
	}

	@Override
	public IMeasurementAdapter createExtensionArtifact() {
		return new DummyDBMeasurement(this);
	}

	

	@Override
	protected void initializeConfigurationParameters() {
	}

	@Override
	public boolean testConnection(String host, String port) {
		boolean connect = false;
		try {
			Client client = LpeWebUtils.getWebClient();
			client.setConnectTimeout(1000 * 60 * 60);
			client.setReadTimeout(1000 * 60 * 60);
			connect = client.resource("http://" + host + ":" + port + "/").path("dummyDB").path("testConnection")
					.accept(MediaType.APPLICATION_JSON).get(Boolean.class);

		} catch (Exception e) {
			connect = false;
		}
		return connect;
	}

	@Override
	public boolean isRemoteExtension() {
		return true;
	}

}
