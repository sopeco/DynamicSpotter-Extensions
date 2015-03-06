package org.spotter.ext.workload.tpcw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeFileUtils;
import org.lpe.common.util.LpeStreamUtils;
import org.lpe.common.util.system.LpeSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.workload.AbstractWorkloadAdapter;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;

public class TpcwRbeDriver extends AbstractWorkloadAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger(TpcwRbeDriver.class);

	public TpcwRbeDriver(IExtension<?> provider) {
		super(provider);
	}

	private String ebFactory;
	private double thinkTimeFactor;
	private int dbNumCustomers;
	private int dbNumItems;
	private String baseUrl;
	private boolean running = false;
	private boolean warmUpTerminated = false;
	private boolean experimentTerminated = false;

	@Override
	public void initialize() throws WorkloadException {
		ebFactory = getProperties().getProperty(TpcwRbeExtension.PAR_EB_FACTORY, TpcwRbeExtension.BROWSING_MIX);
		switch (ebFactory) {
		case TpcwRbeExtension.BROWSING_MIX:
			ebFactory = "rbe.EBTPCW1Factory";
			break;
		case TpcwRbeExtension.SHOPPING_MIX:
			ebFactory = "rbe.EBTPCW2Factory";
			break;
		case TpcwRbeExtension.ORDERING_MIX:
			ebFactory = "rbe.EBTPCW3Factory";
			break;
		default:
			ebFactory = "rbe.EBTPCW1Factory";
		}

		String thinkTimeFactorStr = getProperties().getProperty(TpcwRbeExtension.PAR_THINK_TIME, String.valueOf(1.0));
		thinkTimeFactor = Double.parseDouble(thinkTimeFactorStr);

		String dbNumCustomersStr = getProperties().getProperty(TpcwRbeExtension.PAR_NUM_CUSTOMERS, String.valueOf(10));
		dbNumCustomers = Integer.parseInt(dbNumCustomersStr);

		String dbNumItemsStr = getProperties().getProperty(TpcwRbeExtension.PAR_NUM_ITEMS, String.valueOf(10));
		dbNumItems = Integer.parseInt(dbNumItemsStr);

		baseUrl = getProperties().getProperty(TpcwRbeExtension.PAR_URL, "");

	}

	@Override
	public void startLoad(LoadConfig loadConfig) throws WorkloadException {
		try {
			running = true;
			warmUpTerminated = false;
			experimentTerminated = false;
			final int numEBs = loadConfig.getNumUsers();
			final int rampUpDuration = (int) (((double) numEBs) / ((double) loadConfig.getRampUpUsersPerInterval()) * (double) loadConfig
					.getRampUpIntervalLength());
			final int coolDownDuration = (int) (((double) numEBs) / ((double) loadConfig.getCoolDownUsersPerInterval()) * (double) loadConfig
					.getCoolDownIntervalLength());
			final int duration = loadConfig.getExperimentDuration();

			String tmpDir = LpeSystemUtils.getSystemTempDir();
			tmpDir += tmpDir.endsWith(System.getProperty("file.separator")) ? "rbeTmp" : System
					.getProperty("file.separator") + "rbeTmp";
			File dir = new File(tmpDir);
			if (dir.exists()) {
				LpeFileUtils.removeDir(tmpDir);
			}
			LpeFileUtils.createDir(tmpDir);
			final String filename = tmpDir + System.getProperty("file.separator") + "rbe.jar";
			File file = new File(filename);

			InputStream iStream = getClass().getClassLoader().getResourceAsStream("rbe.jar");
			FileOutputStream foStream = new FileOutputStream(file);
			LpeStreamUtils.pipe(iStream, foStream);

			String whiteSpace = " ";
			final StringBuilder cmdBuilder = new StringBuilder();
			cmdBuilder.append("java -jar ");
			cmdBuilder.append(filename);
			cmdBuilder.append(" -EB " + ebFactory + whiteSpace + numEBs + whiteSpace);
			cmdBuilder.append("-TT " + thinkTimeFactor + whiteSpace);
			cmdBuilder.append("-OUT run.m ");
			cmdBuilder.append("-RU " + rampUpDuration + whiteSpace);
			cmdBuilder.append("-MI " + duration + whiteSpace);
			cmdBuilder.append("-RD " + coolDownDuration + whiteSpace);
			cmdBuilder.append("-GETIM false ");
			cmdBuilder.append("-CUST " + dbNumCustomers + whiteSpace);
			cmdBuilder.append("-ITEM " + dbNumItems + whiteSpace);
			cmdBuilder.append("-WWW " + baseUrl + whiteSpace);
			cmdBuilder.append("-MAXERROR 1000000" + whiteSpace);
			cmdBuilder.append("-INCREMENTAL true"+ whiteSpace);
			

			final long startTime = System.currentTimeMillis();

			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						Process process = Runtime.getRuntime().exec(cmdBuilder.toString());
						BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
						String line =  reader.readLine();
						while(line!=null){
							LOGGER.debug(line);
							line = reader.readLine();
						}
						process.waitFor();
						LOGGER.debug("RBE load terminated!");
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					} finally {
						running = false;
					}
				}
			}).start();
			
			new Thread(new Runnable() {

				@Override
				public void run() {
					while (System.currentTimeMillis() < startTime + (rampUpDuration * 1000L)) {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							break;
						}
					}
					warmUpTerminated = true;
					while (System.currentTimeMillis() < startTime + ((rampUpDuration + duration) * 1000L)) {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							break;
						}
					}
					experimentTerminated = true;
				}
			}).start();

			
		} catch (IOException e1) {
			throw new WorkloadException(e1);
		}
	}

	@Override
	public void waitForWarmupPhaseTermination() throws WorkloadException {
		while (!warmUpTerminated) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	@Override
	public void waitForExperimentPhaseTermination() throws WorkloadException {
		while (!experimentTerminated) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	@Override
	public void waitForFinishedLoad() throws WorkloadException {
		while (running) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				break;
			}
		}

	}

}
