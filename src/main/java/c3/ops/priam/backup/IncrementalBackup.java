/**
 * Copyright 2013 Netflix, Inc.
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
package c3.ops.priam.backup;

import c3.ops.priam.IConfiguration;
import c3.ops.priam.backup.AbstractBackupPath.BackupFileType;
import c3.ops.priam.backup.IMessageObserver.BACKUP_MESSAGE_TYPE;
import c3.ops.priam.scheduler.SimpleTimer;
import c3.ops.priam.scheduler.TaskTimer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
 * Incremental/SSTable backup
 */
@Singleton
public class IncrementalBackup extends AbstractBackup {
  public static final String JOBNAME = "INCR_BACKUP_THREAD";
  private static final Logger logger = LoggerFactory.getLogger(IncrementalBackup.class);
  static List<IMessageObserver> observers = new ArrayList<IMessageObserver>();
  private final List<String> incrementalRemotePaths = new ArrayList<String>();
  private IncrementalMetaData metaData;

  @Inject
  public IncrementalBackup(IConfiguration config, @Named("backup") IBackupFileSystem fs, Provider<AbstractBackupPath> pathFactory
      , IncrementalMetaData metaData) {
    super(config, fs, pathFactory);
    this.metaData = metaData; //a means to upload audit trail (via meta_cf_yyyymmddhhmm.json) of files successfully uploaded
  }

  /**
   * Run every 10 Sec
   */
  public static TaskTimer getTimer() {
    return new SimpleTimer(JOBNAME, 10L * 1000);
  }

  public static void addObserver(IMessageObserver observer) {
    observers.add(observer);
  }

  public static void removeObserver(IMessageObserver observer) {
    observers.remove(observer);
  }

  @Override
  public void execute() throws Exception {
    //Clearing remotePath List
    incrementalRemotePaths.clear();
    File dataDir = new File(config.getDataFileLocation());
    if (!dataDir.exists()) {
      throw new IllegalArgumentException("The configured 'data file location' does not exist: "
          + config.getDataFileLocation());
    }
    logger.debug("Scanning for backup in: {}", dataDir.getAbsolutePath());
    for (File keyspaceDir : dataDir.listFiles()) {
      if (keyspaceDir.isFile())
        continue;
      for (File columnFamilyDir : keyspaceDir.listFiles()) {
        File backupDir = new File(columnFamilyDir, "backups");
        if (!isValidBackupDir(keyspaceDir, columnFamilyDir, backupDir))
          continue;
        List<AbstractBackupPath> uploadedFiles = upload(backupDir, BackupFileType.SST);

        //TODO:  create FP to upload meta data file -- upload audit info of files successfully upload for CF

        if (!uploadedFiles.isEmpty()) {
          String incrementalUploadTime = AbstractBackupPath.formatDate(uploadedFiles.get(0).getTime()); //format of yyyymmddhhmm (e.g. 201505060901)
          String metaFileName = "meta_" + columnFamilyDir.getName() + "_" + incrementalUploadTime;
          logger.info("Uploading meta file for incremental backup: " + metaFileName);
          this.metaData.setMetaFileName(metaFileName);
          this.metaData.set(uploadedFiles, incrementalUploadTime);
          logger.info("Uploaded meta file for incremental backup: " + metaFileName);
        }

      }
    }

    if (incrementalRemotePaths.size() > 0) {
      notifyObservers();
    }

  }

  @Override
  public String getName() {
    return JOBNAME;
  }

  public void notifyObservers() {
    for (IMessageObserver observer : observers) {
      if (observer != null) {
        logger.debug("Updating incremental observers now ...");
        observer.update(BACKUP_MESSAGE_TYPE.INCREMENTAL, incrementalRemotePaths);
      } else
        logger.info("Observer is Null, hence can not notify ...");
    }
  }

  @Override
  protected void addToRemotePath(String remotePath) {
    incrementalRemotePaths.add(remotePath);
  }

}
