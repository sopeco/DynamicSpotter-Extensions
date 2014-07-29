package org.spotter.ext.workload.simple;

public class SimpleVUser implements ISimpleVUser{

	@Override
	public void executeIteration() {
		// nothing to do here
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
