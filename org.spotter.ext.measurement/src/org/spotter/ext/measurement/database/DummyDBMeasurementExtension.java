package org.spotter.ext.measurement.database;

import javax.ws.rs.core.MediaType;

import org.lpe.common.extension.IExtensionArtifact;
import org.lpe.common.util.web.LpeHTTPUtils;
import org.spotter.core.measurement.AbstractMeasurmentExtension;

import com.sun.jersey.api.client.Client;

public class DummyDBMeasurementExtension extends AbstractMeasurmentExtension {


	public DummyDBMeasurementExtension() {
		super(DummyDBMeasurement.class);
	}

	@Override
	protected String getDefaultSatelleiteExtensionName() {
		return "DummyDB Sampling Measurement Satellite Adapter";
	}

	@Override
	protected void initializeConfigurationParameters() {
	}

	@Override
	public boolean testConnection(final String host, final String port) {
		boolean connect = false;
		try {
			final Client client = LpeHTTPUtils.getWebClient();
			client.setConnectTimeout(1000 * 60 * 60);
			client.setReadTimeout(1000 * 60 * 60);
			connect = client.resource("http://" + host + ":" + port + "/").path("dummyDB").path("testConnection")
					.accept(MediaType.APPLICATION_JSON).get(Boolean.class);

		} catch (final Exception e) {
			connect = false;
		}
		return connect;
	}

	@Override
	public boolean isRemoteExtension() {
		return true;
	}

}
