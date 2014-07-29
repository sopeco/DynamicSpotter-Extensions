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
package org.spotter.ext.demo.app;

import java.util.ArrayList;
import java.util.List;

import org.lpe.common.util.web.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A very simple Demo App for DynamicSpotter.
 * 
 * @author Alexander Wert
 * 
 */
public final class DemoAppLauncher {
	private static final int NUM_WORKER_THREADS = 200;
	private static final Logger LOGGER = LoggerFactory.getLogger(DemoAppLauncher.class);
	private static final int DEFAULT_PORT = 8081;
	private static final String PORT_KEY = "port=";

	private static Integer port = DEFAULT_PORT;

	/**
	 * Private constructor due to singleton class.
	 */
	private DemoAppLauncher() {

	}

	/**
	 * Opens up a server on the localhost IP address and the default port 8080
	 * of the underlying system.
	 * 
	 * @param args
	 *            should contain at least one parameter indicating whether to
	 *            start or stop
	 */
	public static void main(String[] args) {

		if (args != null) {
			parseArgs(args);

			if (args.length < 1) {
				printHelpAndExit();
			} else {
				if (args[0].equalsIgnoreCase("start")) {
					List<String> servicePackages = new ArrayList<>();
					servicePackages.add("org.spotter.ext.demo.app");
					WebServer.getInstance()
							.start(port, "", servicePackages, NUM_WORKER_THREADS / 2, NUM_WORKER_THREADS);
				} else if (args[0].equalsIgnoreCase("shutdown")) {
					WebServer.triggerServerShutdown(port, "");
				} else {
					LOGGER.error("Invalid value for 1st argument! Valid values are: start / shutdown");
				}

			}

		} else {
			printHelpAndExit();
		}

	}

	private static void printHelpAndExit() {
		LOGGER.info("LoadRunner Service Launcher requires at least one argument:");
		LOGGER.info("Usage: java -jar <SPOTTER_SERVER_JAR> {start | shutdown} [options]");
		LOGGER.info("the options are:");
		LOGGER.info(PORT_KEY + "=<PORT>: port to bind the server to, default: 8080");
		System.exit(0);
	}

	/**
	 * Parses the agent arguments.
	 * 
	 * @param agentArgs
	 *            arguments as string
	 */
	private static void parseArgs(String[] agentArgs) {
		if (agentArgs == null) {
			return;
		}
		for (String arg : agentArgs) {
			if (arg.startsWith(PORT_KEY)) {
				port = Integer.parseInt(arg.substring(PORT_KEY.length()));
			}
		}
	}

}
