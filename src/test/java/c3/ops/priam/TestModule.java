package c3.ops.priam;

import c3.ops.priam.aws.S3BackupPath;
import c3.ops.priam.backup.AbstractBackupPath;
import c3.ops.priam.backup.FakeCredentials;
import c3.ops.priam.backup.IBackupFileSystem;
import c3.ops.priam.identity.IMembership;
import c3.ops.priam.identity.IPriamInstanceFactory;
import c3.ops.priam.identity.token.*;
import c3.ops.priam.utils.FakeSleeper;
import c3.ops.priam.utils.ITokenManager;
import c3.ops.priam.utils.Sleeper;
import c3.ops.priam.utils.TokenManager;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.junit.Ignore;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

@Ignore
public class TestModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(IConfiguration.class).toInstance(
        new FakeConfiguration(FakeConfiguration.FAKE_REGION, "fake-app", "az1", "fakeInstance1"));
    bind(IPriamInstanceFactory.class).to(FakePriamInstanceFactory.class);
    bind(SchedulerFactory.class).to(StdSchedulerFactory.class).in(Scopes.SINGLETON);
    bind(IMembership.class).toInstance(new FakeMembership(
        ImmutableList.of("fakeInstance1", "fakeInstance2", "fakeInstance3")));
    bind(ICredential.class).to(FakeCredentials.class).in(Scopes.SINGLETON);
    bind(IBackupFileSystem.class).to(NullBackupFileSystem.class);
    bind(AbstractBackupPath.class).to(S3BackupPath.class);
    bind(Sleeper.class).to(FakeSleeper.class);
    bind(ITokenManager.class).to(TokenManager.class);

    bind(IDeadTokenRetriever.class).to(DeadTokenRetriever.class);
    bind(IPreGeneratedTokenRetriever.class).to(PreGeneratedTokenRetriever.class);
    bind(INewTokenRetriever.class).to(NewTokenRetriever.class); //for backward compatibility, unit test always create new tokens
  }
}
