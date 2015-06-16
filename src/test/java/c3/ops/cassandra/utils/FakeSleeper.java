package c3.ops.cassandra.utils;

/**
 * TODO: Replace with a mock object
 */
public class FakeSleeper implements Sleeper {
	@Override
	public void sleep(long waitTimeMs) throws InterruptedException {
		// no-op
	}

	public void sleepQuietly(long waitTimeMs) {
		//no-op
	}
}