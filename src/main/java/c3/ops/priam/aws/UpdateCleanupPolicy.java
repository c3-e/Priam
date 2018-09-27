package c3.ops.priam.aws;

import c3.ops.priam.IConfiguration;
import c3.ops.priam.backup.IBackupFileSystem;
import c3.ops.priam.scheduler.SimpleTimer;
import c3.ops.priam.scheduler.Task;
import c3.ops.priam.scheduler.TaskTimer;
import c3.ops.priam.utils.RetryableCallable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/**
 * Updates the cleanup policy for the bucket
 *
 */
@Singleton
public class UpdateCleanupPolicy extends Task
{
  public static final String JOBNAME = "UpdateCleanupPolicy";
  private IBackupFileSystem fs;

  @Inject
  public UpdateCleanupPolicy(IConfiguration config,@Named("backup")IBackupFileSystem fs)
  {
    super(config);
    this.fs = fs;
  }

  @Override
  public void execute() throws Exception
  {
    // Set cleanup policy of retention is specified
    new RetryableCallable<Void>()
    {
      @Override
      public Void retriableCall() throws Exception
      {
        fs.cleanup();
        return null;
      }
    }.call();
  }

  @Override
  public String getName()
  {
    return JOBNAME;
  }

  public static TaskTimer getTimer()
  {
    return new SimpleTimer(JOBNAME);
  }

}
