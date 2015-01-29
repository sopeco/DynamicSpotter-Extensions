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
package org.spotter.ext.detection.appHiccups.utils;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;

/**
 * Configuration for hiccup detection.
 * 
 * @author Alexander Wert
 * 
 */
public class HiccupDetectionConfig {
	public static final String MOVING_AVERAGE_WINDOW_SIZE_KEY = "mvaWindowSize";
	public static final int MOVING_AVERAGE_WINDOW_SIZE_DEFAULT = 11;
	private int mvaWindowSize; // should be an odd number



	/**
	 * @return the mvaWindowSize
	 */
	public int getMvaWindowSize() {
		return mvaWindowSize;
	}

	/**
	 * @param mvaWindowSize
	 *            the mvaWindowSize to set
	 */
	public void setMvaWindowSize(int mvaWindowSize) {
		this.mvaWindowSize = mvaWindowSize;
	}

	/**
	 * 
	 * @return set of configuration parameters for hiccup detection
	 */
	public static  Set<ConfigParameterDescription> getConfigurationParameters() {
		ConfigParameterDescription mvaWindowSizeParameter = new ConfigParameterDescription(
				MOVING_AVERAGE_WINDOW_SIZE_KEY, LpeSupportedTypes.Integer);
		mvaWindowSizeParameter.setDescription("ONLY for Moving Average Analysis Strategy! Defines the window size for calculating "
				+ "the moving average on a response time series.");
		mvaWindowSizeParameter.setDefaultValue(String.valueOf(MOVING_AVERAGE_WINDOW_SIZE_DEFAULT));
		mvaWindowSizeParameter.setMandatory(false);
		
		
		
		Set<ConfigParameterDescription> set = new HashSet<>();
		set.add(mvaWindowSizeParameter);
		return set;
	}



}
