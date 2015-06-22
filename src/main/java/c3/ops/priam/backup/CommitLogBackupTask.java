package c3.ops.priam.backup;


import c3.ops.priam.IConfiguration;
import c3.ops.priam.backup.IMessageObserver.BACKUP_MESSAGE_TYPE;
import c3.ops.priam.scheduler.SimpleTimer;
import c3.ops.priam.scheduler.TaskTimer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


//Provide this to be run as a Quart job
@Singleton
public class CommitLogBackupTask extends AbstractBackup {
  private static final Logger logger = LoggerFactory.getLogger(SnapshotBackup.class);
  public static String JOBNAME = "CommitLogBackup";
  static List<IMessageObserver> observers = new ArrayList<IMessageObserver>();
  private final List<String> clRemotePaths = new ArrayList<String>();
  private final CommitLogBackup clBackup;


  @Inject
  public CommitLogBackupTask(IConfiguration config, @Named("backup") IBackupFileSystem fs, Provider<AbstractBackupPath> pathFactory,
                             CommitLogBackup clBackup) {
    super(config, fs, pathFactory);
    this.clBackup = clBackup;
  }

  public static TaskTimer getTimer(IConfiguration config) {
    return new SimpleTimer(JOBNAME, 60L * 1000); //every 1 min
  }

  public static void addObserver(IMessageObserver observer) {
    observers.add(observer);
  }

  public static void removeObserver(IMessageObserver observer) {
    observers.remove(observer);
  }

  @Override
  public void execute() throws Exception {
    try {
      logger.debug("Checking for any archived commitlogs");
      //double-check the permission
      if (config.isBackingUpCommitLogs())
        clBackup.upload(config.getCommitLogBackupRestoreFromDirs(), null);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  @Override
  public String getName() {
    return JOBNAME;
  }

  public void notifyObservers() {
    for (IMessageObserver observer : observers) {
      if (observer != null) {
        logger.debug("Updating CL observers now ...");
        observer.update(BACKUP_MESSAGE_TYPE.COMMITLOG, clRemotePaths);
      } else
        logger.info("Observer is Null, hence can not notify ...");
    }
  }

  @Override
  protected void addToRemotePath(String remotePath) {
    clRemotePaths.add(remotePath);
  }

}
