package org.spotter.ext.detection.olb;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.probes.SQLQueryProbe;
import org.aim.artifacts.sampler.CPUSampler;
import org.aim.artifacts.sampler.NetworkIOSampler;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.artifacts.scopes.JDBCScope;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.olb.strategies.QTStrategy;
import org.spotter.ext.detection.olb.strategies.TTestCpuThresholdStrategy;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Detection controller for the One Lane Bridge anti-pattern.
 * 
 * @author Alexander Wert
 * 
 */
public class OLBDetectionController extends AbstractDetectionController implements IExperimentReuser {

	private static final int SAMPLING_DELAY = 100;

	private int experimentSteps;
	private String analysisStrategy;
	private String scope;
	private IOLBAnalysisStrategy analysisStrategyImpl;
	private boolean reuser = false;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public OLBDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		String experimentStepsStr = getProblemDetectionConfiguration().getProperty(OLBExtension.EXPERIMENT_STEPS_KEY);
		experimentSteps = experimentStepsStr != null ? Integer.parseInt(experimentStepsStr)
				: OLBExtension.EXPERIMENT_STEPS_DEFAULT;

		scope = getProblemDetectionConfiguration().getProperty(OLBExtension.SCOPE_KEY, OLBExtension.ENTRY_SCOPE);

		analysisStrategy = getProblemDetectionConfiguration().getProperty(OLBExtension.DETECTION_STRATEGY_KEY,
				OLBExtension.QUEUEING_THEORY_STRATEGY);

		switch (analysisStrategy) {
		case OLBExtension.QUEUEING_THEORY_STRATEGY:
			analysisStrategyImpl = new QTStrategy();
			break;
		case OLBExtension.T_TEST_CPU_THRESHOLD_STRATEGY:
			analysisStrategyImpl = new TTestCpuThresholdStrategy();
			break;
		default:
			analysisStrategyImpl = new QTStrategy();
		}
		analysisStrategyImpl.setMainDetectionController(this);
		analysisStrategyImpl.setProblemDetectionConfiguration(getProblemDetectionConfiguration());
		reuser = Boolean.parseBoolean(this.getProblemDetectionConfiguration().getProperty(
				AbstractDetectionExtension.REUSE_EXPERIMENTS_FROM_PARENT, "false"));
	}

	@Override
	public long getExperimentSeriesDuration() {

		if (reuser) {
			return 0;
		} else {
			return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(experimentSteps);
		}

	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		if (!reuser) {
			// TODO: fix that
			if (scope.equals(OLBExtension.DB_SCOPE)) {
				instrumentApplication(getInstrumentationDescription(1.0));
				runExperiment(this, 1);
				uninstrumentApplication();

				instrumentApplication(getInstrumentationDescription(1.0));
				runExperiment(this, 125);
				uninstrumentApplication();

				instrumentApplication(getInstrumentationDescription(0.1));
				runExperiment(this, 250);
				uninstrumentApplication();

				instrumentApplication(getInstrumentationDescription(0.1));
				runExperiment(this, 375);
				uninstrumentApplication();

				instrumentApplication(getInstrumentationDescription(0.05));
				runExperiment(this, 500);
				uninstrumentApplication();
			} else {
				executeDefaultExperimentSeries(this, experimentSteps, getInstrumentationDescription());
			}

		}
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		return analysisStrategyImpl.analyze(data);
	}

	@Override
	public InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		if (!reuser) {
			switch (scope) {
			case OLBExtension.SYNC_SCOPE:
				// idBuilder
				// .newMethodScopeEntity("tpcw.TPCW_Database.cartUpdateSynchronized*",
				// "tpcw.TPCW_Database.getConnection()",
				// "tpcw.TPCW_Database.returnConnection(*",
				// "tpcw.TPCW_Database.createEmptyCart(java.sql.Connection*",
				// "tpcw.TPCW_Database.createCustomer*",
				// "tpcw.TPCW_Database.insertAddress*",
				// "tpcw.TPCW_Database.newOrder*")
				// .addProbe(ResponsetimeProbe.MODEL_PROBE).entityDone();
				
				// idBuilder.newSynchronizedScopeEntity().addProbe(MonitorWaitingTimeProbe.MODEL_PROBE).entityDone();
				
				idBuilder.newMethodScopeEntity("Nop.Web.Controllers.ShoppingCartController:ProblemMethod")
						.addProbe(ResponsetimeProbe.MODEL_PROBE).entityDone();
				break;
			case OLBExtension.DB_SCOPE:
				idBuilder.newAPIScopeEntity(JDBCScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
						.addProbe(SQLQueryProbe.MODEL_PROBE).entityDone();
				idBuilder.newGlobalRestriction().setGranularity(0.01).restrictionDone();
				break;
			case OLBExtension.ENTRY_SCOPE:
				idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
						.entityDone();
				break;
			default:
				idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
						.entityDone();
				break;
			}

		}
		idBuilder.newSampling(CPUSampler.class.getName(), SAMPLING_DELAY);
		idBuilder.newSampling(NetworkIOSampler.class.getName(), SAMPLING_DELAY);
		return idBuilder.build();
	}

	public InstrumentationDescription getInstrumentationDescription(double granularity) {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();

		idBuilder.newAPIScopeEntity(JDBCScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.addProbe(SQLQueryProbe.MODEL_PROBE).entityDone();
		idBuilder.newGlobalRestriction().setGranularity(granularity).restrictionDone();

		idBuilder.newSampling(CPUSampler.class.getName(), SAMPLING_DELAY);
		idBuilder.newSampling(NetworkIOSampler.class.getName(), SAMPLING_DELAY);
		return idBuilder.build();
	}

}
