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
import c3.ops.priam.aws.S3BackupPath;
import c3.ops.priam.identity.InstanceIdentity;
import com.google.inject.ImplementedBy;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.io.util.RandomAccessReader;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Date;
import java.util.regex.Pattern;

@ImplementedBy(S3BackupPath.class)
public abstract class AbstractBackupPath implements Comparable<AbstractBackupPath> {
  private static final Logger logger = LoggerFactory.getLogger(AbstractBackupPath.class);
  public static final char PATH_SEP = File.separatorChar;
  public static final Pattern clPattern = Pattern.compile(".*CommitLog-(\\d{13}).log");
  private static final String FMT = "yyyyMMddHHmm";
  private final MessageDigest md;
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern(FMT);
  protected final InstanceIdentity factory;


  protected final IConfiguration config;
  protected BackupFileType type;
  protected String clusterName;
  protected String keyspace;
  protected String columnFamily;
  protected String fileName;
  protected String baseDir;
  protected String token;
  protected String region;
  protected Date time;
  protected long size;
  protected boolean isCassandra1_0;
  protected File backupFile;
  protected Date uploadedTs;
  protected String checksum;

  public AbstractBackupPath(IConfiguration config, InstanceIdentity factory) throws NoSuchAlgorithmException {
    this.factory = factory;
    this.config = config;
    this.md = MessageDigest.getInstance("MD5");
  }

  public static String formatDate(Date d) {
    return new DateTime(d).toString(FMT);
  }

  public Date parseDate(String s) {
    return DATE_FORMAT.parseDateTime(s).toDate();
  }

  public InputStream localReader() throws IOException {
    assert backupFile != null;
    return new RafInputStream(RandomAccessReader.open(backupFile));
  }

  public void parseLocal(File file, BackupFileType type) throws ParseException, IOException {
    // TODO cleanup.
    this.backupFile = file;

    String rpath = new File(config.getDataFileLocation()).toURI().relativize(file.toURI()).getPath();
    String[] elements = rpath.split("" + PATH_SEP);
    this.clusterName = config.getAppName();
    this.baseDir = config.getBackupLocation();
    this.region = config.getDC();
    this.token = factory.getInstance().getToken();
    this.type = type;
    if (type != BackupFileType.META && type != BackupFileType.CL) {
      this.keyspace = elements[0];
      if (!isCassandra1_0)
        this.columnFamily = elements[1];
    }
    if (type == BackupFileType.SNAP)
      time = parseDate(elements[3]);
    if (type == BackupFileType.SST || type == BackupFileType.CL)
      time = new Date(file.lastModified());
    this.fileName = file.getName();
    this.size = file.length();
    this.checksum = calculateChecksum(localReader());

  }

  /**
   * Given a date range, find a common string prefix Eg: 20120212, 20120213 =>
   * 2012021
   */
  public String match(Date start, Date end) {
    String sString = formatDate(start);
    String eString = formatDate(end);
    int diff = StringUtils.indexOfDifference(sString, eString);
    if (diff < 0)
      return sString;
    return sString.substring(0, diff);
  }

  /**
   * Local restore file
   */
  public File newRestoreFile() {
    StringBuffer buff = new StringBuffer();
    if (type == BackupFileType.CL) {
      buff.append(config.getBackupCommitLogLocation()).append(PATH_SEP);
    } else {

      buff.append(config.getDataFileLocation()).append(PATH_SEP);
      if (type != BackupFileType.META) {
        if (isCassandra1_0)
          buff.append(keyspace).append(PATH_SEP);
        else
          buff.append(keyspace).append(PATH_SEP).append(columnFamily).append(PATH_SEP);
      }
    }

    buff.append(fileName);

    File return_ = new File(buff.toString());
    File parent = new File(return_.getParent());
    if (!parent.exists())
      parent.mkdirs();
    return return_;
  }

  protected String calculateChecksum(InputStream is) {
    String checkSum = "";
    try{
      InputStream fis = is;
      byte[] dataBytes = new byte[1024];

      int nRead = 0;

      while ((nRead = fis.read(dataBytes)) != -1) {
        md.update(dataBytes, 0, nRead);
      }

      byte[] mdBytes = md.digest();

      StringBuffer sb = new StringBuffer("");
      for (int i = 0; i < mdBytes.length; i++) {
        sb.append(Integer.toString((mdBytes[i] & 0xff) + 0x100, 16).substring(1));
      }
      checkSum = sb.toString();
    }catch (IOException e){
      logger.warn("Checksum calculation failed for -> "+this.getFileName());
    }

    return checkSum;
  }

  /**
   * Parses a fully constructed remote path
   */
  protected void setRemoteFileChecksum(String remoteFileChecksum){
    checksum = remoteFileChecksum;
  }

  @Override
  public int compareTo(AbstractBackupPath o) {
    return getRemotePath().compareTo(o.getRemotePath());
  }

  @Override
  public boolean equals(Object obj) {
    if (!obj.getClass().equals(this.getClass()))
      return false;
    return getRemotePath().equals(((AbstractBackupPath) obj).getRemotePath());
  }

  /**
   * Get remote prefix for this path object
   */
  public abstract String getRemotePath();

  /**
   * Parses a fully constructed remote path
   */
  public abstract void parseRemote(String remoteFilePath);

  /**
   * Parses paths with just token prefixes
   */
  public abstract void parsePartialPrefix(String remoteFilePath);

  /**
   * Provides a common prefix that matches all objects that fall between
   * the start and end time
   */
  public abstract String remotePrefix(Date start, Date end, String location);

  /**
   * Provides the cluster prefix
   */
  public abstract String clusterPrefix(String location);

  public BackupFileType getType() {
    return type;
  }

  public String getClusterName() {
    return clusterName;
  }

  public String getKeyspace() {
    return keyspace;
  }

  public String getColumnFamily() {
    return columnFamily;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getBaseDir() {
    return baseDir;
  }

  public String getToken() {
    return token;
  }

  public String getRegion() {
    return region;
  }

  public Date getTime() {
    return time;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public File getBackupFile() {
    return backupFile;
  }

  public boolean isCassandra1_0() {
    return isCassandra1_0;
  }

  public void setCassandra1_0(boolean isCassandra1_0) {
    this.isCassandra1_0 = isCassandra1_0;
  }

  public InstanceIdentity getInstanceIdentity() {
    return this.factory;
  }

  public Date getUploadedTs() {
    return this.uploadedTs;
  }

  public String getChecksum() {
    return this.checksum;
  }

  public void setUploadedTs(Date uploadedTs) {
    this.uploadedTs = uploadedTs;
  }

  @Override
  public String toString() {
    return "From: " + getRemotePath() + " To: " + newRestoreFile().getPath();
  }

  public static enum BackupFileType {
    SNAP, SST, CL, META
  }

  public static class RafInputStream extends InputStream {
    private RandomAccessFile raf;

    public RafInputStream(RandomAccessFile raf) {
      this.raf = raf;
    }

    @Override
    public synchronized int read(byte[] bytes, int off, int len) throws IOException {
      return raf.read(bytes, off, len);
    }

    @Override
    public void close() {
      FileUtils.closeQuietly(raf);
    }

    @Override
    public int read() throws IOException {
      return 0;
    }
  }
}
