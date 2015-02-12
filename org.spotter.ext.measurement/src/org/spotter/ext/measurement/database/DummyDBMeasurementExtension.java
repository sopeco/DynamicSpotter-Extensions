package org.spotter.ext.measurement.database;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.measurement.AbstractMeasurmentExtension;
import org.spotter.core.measurement.IMeasurementAdapter;

public class DummyDBMeasurementExtension extends AbstractMeasurmentExtension {

	public static final String HOST = "host";
	public static final String PORT = "port";

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



	private ConfigParameterDescription createHostParameter() {
		ConfigParameterDescription samplingDelayParameter = new ConfigParameterDescription(HOST,
				LpeSupportedTypes.String);
		samplingDelayParameter.setMandatory(true);
		samplingDelayParameter.setDescription("The host / ip of the database");

		return samplingDelayParameter;
	}

	private ConfigParameterDescription createPortParameter() {
		ConfigParameterDescription collectorTypeParameter = new ConfigParameterDescription(PORT,
				LpeSupportedTypes.String);
		collectorTypeParameter.setMandatory(true);
		collectorTypeParameter.setDescription("Port of the database");

		return collectorTypeParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createHostParameter());
		addConfigParameter(createPortParameter());
	}

	@Override
	public boolean testConnection(String host, String port) {
		return true;
	}

	@Override
	public boolean isRemoteExtension() {
		return false;
	}

}
