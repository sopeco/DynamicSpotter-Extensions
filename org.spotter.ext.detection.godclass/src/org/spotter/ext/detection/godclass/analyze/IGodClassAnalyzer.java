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
package org.spotter.ext.detection.godclass.analyze;

import org.spotter.ext.detection.godclass.processor.data.ProcessedData;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Interface for god class analysis strategies.
 * 
 * @author Alexander Wert
 * 
 */
public interface IGodClassAnalyzer {
	/**
	 * Analyse the processData to identify a god class.
	 * 
	 * @param processData
	 *            data to analyse
	 * @param result
	 *            result object where to report a detection
	 */
	void analyze(ProcessedData processData, SpotterResult result);
}
