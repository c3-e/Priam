package c3.ops.priam;

import c3.ops.priam.backup.AbstractBackupPath;
import c3.ops.priam.backup.BackupRestoreException;
import c3.ops.priam.backup.IBackupFileSystem;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;

public class NullBackupFileSystem implements IBackupFileSystem {

  @Override
  public Iterator<AbstractBackupPath> list(String bucket, Date start, Date till) {
    return null;
  }

  @Override
  public int getActivecount() {
    return 0;
  }

  public void shutdown() {
    //NOP
  }

  @Override
  public void download(AbstractBackupPath path, OutputStream os) throws BackupRestoreException {
  }

  @Override
  public void upload(AbstractBackupPath path, InputStream in) throws BackupRestoreException {
  }

  @Override
  public Iterator<AbstractBackupPath> listPrefixes(Date date) {
    return null;
  }

  @Override
  public void cleanup() {
    // TODO Auto-generated method stub

  }

  @Override
  public void download(AbstractBackupPath path, OutputStream os,
                       String filePath) throws BackupRestoreException {
    // TODO Auto-generated method stub

  }
}