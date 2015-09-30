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
package org.spotter.ext.dummy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.entities.measurements.AbstractRecord;
import org.aim.aiminterface.entities.measurements.MeasurementData;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.artifacts.records.CPUUtilizationRecord;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.lpe.common.extension.IExtension;
import org.spotter.core.measurement.AbstractMeasurementAdapter;

public class TestMeasurement extends AbstractMeasurementAdapter {

	public TestMeasurement(final IExtension provider) {
		super(provider);
	}

	@Override
	public void enableMonitoring() throws MeasurementException {

	}

	@Override
	public void disableMonitoring() throws MeasurementException {

	}

	@Override
	public MeasurementData getMeasurementData() throws MeasurementException {
		final List<AbstractRecord> records = new ArrayList<>();
		final Random rand = new Random();

		for (long i = 0; i < Integer.parseInt(getProperties().getProperty(TestMeasurementExtension.NUM_RECORDS, "100")); i++) {
			final ResponseTimeRecord rtRecord = new ResponseTimeRecord(System.currentTimeMillis() + i * 10L, "operation-"
					+ (i % 5), (long) (rand.nextDouble() * 100L));
			final CPUUtilizationRecord cpuRecord = new CPUUtilizationRecord(System.currentTimeMillis() + i * 10L, "CPU-"
					+ (i % 2), rand.nextDouble());
			final CPUUtilizationRecord cpuRecordAgg = new CPUUtilizationRecord(System.currentTimeMillis() + i * 10L, CPUUtilizationRecord.RES_CPU_AGGREGATED, rand.nextDouble());
			records.add(rtRecord);
			records.add(cpuRecord);

			records.add(cpuRecordAgg);

		}

		final MeasurementData mData = new MeasurementData();
		mData.setRecords(records);

		return mData;
	}

	@Override
	public void pipeToOutputStream(final OutputStream oStream) throws MeasurementException {
		BufferedWriter writer = null;
		try {

			final List<AbstractRecord> recordList = getMeasurementData().getRecords();
			writer = new BufferedWriter(new OutputStreamWriter(oStream), 1024);

			for (final AbstractRecord rec : recordList) {
				writer.write(rec.toString());
				writer.newLine();
			}

		} catch (final IOException e) {
			throw new MeasurementException(e);
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (final IOException e) {
					throw new MeasurementException(e);
				}
			}
		}

	}

	@Override
	public void initialize() throws MeasurementException {

	}

	@Override
	public long getCurrentTime() {
		return System.currentTimeMillis();
	}

	@Override
	public void storeReport(final String path) throws MeasurementException {

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
