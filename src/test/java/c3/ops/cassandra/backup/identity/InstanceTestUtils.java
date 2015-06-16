package c3.ops.cassandra.backup.identity;

import c3.ops.cassandra.FakeConfiguration;
import c3.ops.cassandra.FakeMembership;
import c3.ops.cassandra.FakePriamInstanceFactory;
import c3.ops.cassandra.identity.IMembership;
import c3.ops.cassandra.identity.IPriamInstanceFactory;
import c3.ops.cassandra.identity.InstanceIdentity;
import c3.ops.cassandra.identity.token.DeadTokenRetriever;
import c3.ops.cassandra.identity.token.NewTokenRetriever;
import c3.ops.cassandra.identity.token.PreGeneratedTokenRetriever;
import c3.ops.cassandra.utils.FakeSleeper;
import c3.ops.cassandra.utils.ITokenManager;
import c3.ops.cassandra.utils.Sleeper;
import c3.ops.cassandra.utils.TokenManager;
import org.junit.Before;
import org.junit.Ignore;

import java.util.ArrayList;
import java.util.List;

@Ignore
public abstract class InstanceTestUtils {

	private static final ITokenManager tokenManager = new TokenManager();
	List<String> instances = new ArrayList<String>();
	IMembership membership;
	FakeConfiguration config;
	IPriamInstanceFactory factory;
	InstanceIdentity identity;
	Sleeper sleeper;
	DeadTokenRetriever deadTokenRetriever;
	PreGeneratedTokenRetriever preGeneratedTokenRetriever;
	NewTokenRetriever newTokenRetriever;

	@Before
	public void setup() {
		instances.add("fakeinstance1");
		instances.add("fakeinstance2");
		instances.add("fakeinstance3");
		instances.add("fakeinstance4");
		instances.add("fakeinstance5");
		instances.add("fakeinstance6");
		instances.add("fakeinstance7");
		instances.add("fakeinstance8");
		instances.add("fakeinstance9");

		membership = new FakeMembership(instances);
		config = new FakeConfiguration("fake", "fake-app", "az1", "fakeinstance1");
		factory = new FakePriamInstanceFactory(config);
		sleeper = new FakeSleeper();
		this.deadTokenRetriever = new DeadTokenRetriever(factory, membership, config, sleeper);
		this.preGeneratedTokenRetriever = new PreGeneratedTokenRetriever(factory, membership, config, sleeper);
		this.newTokenRetriever = new NewTokenRetriever(factory, membership, config, sleeper, tokenManager);
	}

	public void createInstances() throws Exception {
		createInstanceIdentity("az1", "fakeinstance1");
		createInstanceIdentity("az1", "fakeinstance2");
		createInstanceIdentity("az1", "fakeinstance3");
		// try next region
		createInstanceIdentity("az2", "fakeinstance4");
		createInstanceIdentity("az2", "fakeinstance5");
		createInstanceIdentity("az2", "fakeinstance6");
		// next region
		createInstanceIdentity("az3", "fakeinstance7");
		createInstanceIdentity("az3", "fakeinstance8");
		createInstanceIdentity("az3", "fakeinstance9");
	}

	protected InstanceIdentity createInstanceIdentity(String zone, String instanceId) throws Exception {
		config.zone = zone;
		config.instance_id = instanceId;
		return new InstanceIdentity(factory, membership, config, sleeper, new TokenManager()
				, this.deadTokenRetriever
				, this.preGeneratedTokenRetriever
				, this.newTokenRetriever
		);
	}
}
