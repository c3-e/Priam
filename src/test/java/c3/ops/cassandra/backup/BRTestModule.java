package c3.ops.cassandra.backup;

import c3.ops.cassandra.*;
import c3.ops.cassandra.aws.S3BackupPath;
import c3.ops.cassandra.compress.ICompression;
import c3.ops.cassandra.compress.SnappyCompression;
import c3.ops.cassandra.defaultimpl.CassandraProcessManager;
import c3.ops.cassandra.identity.IMembership;
import c3.ops.cassandra.identity.IPriamInstanceFactory;
import c3.ops.cassandra.identity.token.*;
import c3.ops.cassandra.utils.FakeSleeper;
import c3.ops.cassandra.utils.ITokenManager;
import c3.ops.cassandra.utils.Sleeper;
import c3.ops.cassandra.utils.TokenManager;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import org.junit.Ignore;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Arrays;

@Ignore
public class BRTestModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(IConfiguration.class).toInstance(new FakeConfiguration(FakeConfiguration.FAKE_REGION, "fake-app", "az1", "fakeInstance1"));
		bind(IPriamInstanceFactory.class).to(FakePriamInstanceFactory.class);
		bind(SchedulerFactory.class).to(StdSchedulerFactory.class).in(Scopes.SINGLETON);
		bind(IMembership.class).toInstance(new FakeMembership(Arrays.asList("fakeInstance1")));
		bind(ICredential.class).to(FakeNullCredential.class).in(Scopes.SINGLETON);
//        bind(IBackupFileSystem.class).to(FakeBackupFileSystem.class).in(Scopes.SINGLETON);
		bind(IBackupFileSystem.class).annotatedWith(Names.named("backup")).to(FakeBackupFileSystem.class).in(Scopes.SINGLETON);
		bind(IBackupFileSystem.class).annotatedWith(Names.named("incr_restore")).to(FakeBackupFileSystem.class).in(Scopes.SINGLETON);
		bind(AbstractBackupPath.class).to(S3BackupPath.class);
		bind(ICompression.class).to(SnappyCompression.class);
		bind(Sleeper.class).to(FakeSleeper.class);
		bind(ITokenManager.class).to(TokenManager.class);
		bind(ICassandraProcess.class).to(CassandraProcessManager.class);

		bind(IDeadTokenRetriever.class).to(DeadTokenRetriever.class);
		bind(IPreGeneratedTokenRetriever.class).to(PreGeneratedTokenRetriever.class);
		bind(INewTokenRetriever.class).to(NewTokenRetriever.class); //for backward compatibility, unit test always create new tokens
	}
}
