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
package org.spotter.ext.loadrunner.measurement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.entities.measurements.AbstractRecord;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.lpe.common.extension.IExtension;
import org.lpe.common.loadgenerator.LoadGeneratorClient;
import org.lpe.common.loadgenerator.config.LGMeasurementConfig;
import org.lpe.common.loadgenerator.data.LGMeasurementData;
import org.lpe.common.loadgenerator.data.TimeSpan;
import org.lpe.common.util.LpeStringUtils;
import org.spotter.core.instrumentation.InstrumentationBroker;
import org.spotter.core.measurement.AbstractMeasurementAdapter;
import org.spotter.ext.loadrunner.LRConfigKeys;
import org.spotter.ext.loadrunner.instrumentation.LoadRunnerInstrumentationClient;

/**
 * The LoadRunner measurement client which communicates with the LoadRunner
 * measurement resource.
 * 
 * @author Le-Huan Stefan Tran
 */
public class LoadRunnerMeasurementClient extends AbstractMeasurementAdapter {
	private LGMeasurementConfig lrmConfig;
	private LoadGeneratorClient lrClient;
	private LoadRunnerInstrumentationClient instrumentationClient = null;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public LoadRunnerMeasurementClient(final IExtension provider) {
		super(provider);

	}

	@Override
	public void initialize() throws MeasurementException {

		for (final LoadRunnerInstrumentationClient instrClient : InstrumentationBroker.getInstance()
				.getInstrumentationControllers(LoadRunnerInstrumentationClient.class)) {
			if (instrClient.getHost().equals(getHost()) && instrClient.getPort().equals(getPort())) {
				instrumentationClient = instrClient;
			}
		}

		if (lrClient == null) {
			lrClient = new LoadGeneratorClient(getHost(), getPort());
			lrmConfig = new LGMeasurementConfig();

			lrmConfig.setAnalysisPath(LpeStringUtils
					.getPropertyOrFail(getProperties(), LRConfigKeys.ANALYSIS_EXE, null));
			lrmConfig.setAnalysisTemplate(LpeStringUtils.getPropertyOrFail(getProperties(),
					LRConfigKeys.ANALYSIS_TEMPLATE_NAME, null));
			lrmConfig.setResultDir(LpeStringUtils.getPropertyOrFail(getProperties(), LRConfigKeys.RESULT_DIR, null));
			lrmConfig.setSessionName(LpeStringUtils.getPropertyOrFail(getProperties(),
					LRConfigKeys.ANALYSIS_SESSION_NAME, null));

			if (!lrClient.testConnection()) {
				throw new MeasurementException("Connection to loadrunner could not be established!");
			}
		}
	}

	@Override
	public void enableMonitoring() throws MeasurementException {
		// Nothing to do here.
	}

	@Override
	public void disableMonitoring() throws MeasurementException {
		// Nothing to do here.
	}

	@Override
	public MeasurementData getMeasurementData() throws MeasurementException {
		if (lrmConfig == null) {
			throw new MeasurementException("LoadRunner Measurement Client has not been initialized yet!");
		}
		if (instrumentationClient != null && instrumentationClient.isInstrumented()) {
			final LGMeasurementData lgData = lrClient.getMeasurementData(lrmConfig);
			final List<AbstractRecord> records = new ArrayList<>();
			for (final String transactionName : lgData.getTransactionNames()) {
				for (final TimeSpan tSpan : lgData.getTimesForTransaction(transactionName)) {
					records.add(new ResponseTimeRecord(tSpan.getStart(), transactionName, tSpan.getStop()-tSpan.getStart()));
				}
			}

			final MeasurementData data = new MeasurementData();
			data.setRecords(records);
			return data;

		} else {
			return new MeasurementData();
		}

	}

	@Override
	public long getCurrentTime() {
		return lrClient.getCurrentTime();

	}

	@Override
	public void setControllerRelativeTime(final long relativeTime) {
		// Loadrunner Timestamps are alredy relative
		super.setControllerRelativeTime(0);
	}

	@Override
	public void pipeToOutputStream(final OutputStream oStream) throws MeasurementException {
		if (lrmConfig == null) {
			throw new MeasurementException("LoadRunner Measurement Client has not been initialized yet!");
		}
		final BufferedWriter bWriter = new BufferedWriter(new OutputStreamWriter(oStream));

		try {
			final MeasurementData data = getMeasurementData();
			for (final AbstractRecord record : data.getRecords()) {
				final String line = record.toString();
				bWriter.write(line);
				bWriter.newLine();
			}
		} catch (final IOException e) {
			throw new MeasurementException(e);
		} finally {
			try {
				bWriter.flush();
				oStream.close();
			} catch (final IOException e) {
				throw new MeasurementException(e);
			}
		}

	}

	@Override
	public void storeReport(final String path) throws MeasurementException {
		final String reportPath = path + System.getProperty("file.separator") + "LRReport";
		final File reportDir = new File(reportPath);
		reportDir.mkdir();
		final File reportZip = new File(reportPath + System.getProperty("file.separator") + "report.zip");

		try {
			final FileOutputStream fos = new FileOutputStream(reportZip);
			lrClient.pipeReportToOutputStream(fos, lrmConfig);
		} catch (final IOException e) {
			throw new MeasurementException(e);
		}
	}

	@Override
	public void prepareMonitoring(final InstrumentationDescription monitoringDescription) throws MeasurementException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resetMonitoring() throws MeasurementException {
		// TODO Auto-generated method stub
		
	}

}
