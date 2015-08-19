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
package c3.ops.priam.resources;

import c3.ops.priam.ICassandraProcess;
import c3.ops.priam.IConfiguration;
import c3.ops.priam.PriamServer;
import c3.ops.priam.backup.*;
import c3.ops.priam.backup.AbstractBackupPath.BackupFileType;
import c3.ops.priam.identity.IPriamInstanceFactory;
import c3.ops.priam.identity.PriamInstance;
import c3.ops.priam.scheduler.PriamScheduler;
import c3.ops.priam.utils.CassandraTuner;
import c3.ops.priam.utils.ITokenManager;
import c3.ops.priam.utils.SystemUtils;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.math.BigInteger;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Path("/v1/backup")
@Produces(MediaType.APPLICATION_JSON)
public class BackupServlet {
  private static final Logger logger = LoggerFactory.getLogger(BackupServlet.class);

  private static final String REST_SUCCESS = "[\"ok\"]";
  private static final String REST_HEADER_RANGE = "daterange";
  private static final String REST_HEADER_FILTER = "filter";
  private static final String REST_HEADER_TOKEN = "token";
  private static final String REST_HEADER_REGION = "region";
  private static final String REST_KEYSPACES = "keyspaces";
  private static final String REST_RESTORE_PREFIX = "restoreprefix";
  private static final String FMT = "yyyyMMddHHmm";

  private final ITokenManager tokenManager;
  private final ICassandraProcess cassProcess;
  private PriamServer priamServer;
  private IConfiguration config;
  private IBackupFileSystem backupFs;
  private IBackupFileSystem bkpStatusFs;
  private Restore restoreObj;
  private Provider<AbstractBackupPath> pathProvider;
  private CassandraTuner tuner;
  private SnapshotBackup snapshotBackup;
  private IPriamInstanceFactory factory;
  @Inject
  private PriamScheduler scheduler;
  @Inject
  private MetaData metaData;

  @Inject
  public BackupServlet(PriamServer priamServer, IConfiguration config, @Named("backup") IBackupFileSystem backupFs, @Named("backup_status") IBackupFileSystem bkpStatusFs, Restore restoreObj, Provider<AbstractBackupPath> pathProvider, CassandraTuner tuner,
                       SnapshotBackup snapshotBackup, IPriamInstanceFactory factory, ITokenManager tokenManager, ICassandraProcess cassProcess)

  {
    this.priamServer = priamServer;
    this.config = config;
    this.backupFs = backupFs;
    this.bkpStatusFs = bkpStatusFs;
    this.restoreObj = restoreObj;
    this.pathProvider = pathProvider;
    this.tuner = tuner;
    this.snapshotBackup = snapshotBackup;
    this.factory = factory;
    this.tokenManager = tokenManager;
    this.cassProcess = cassProcess;
  }

  @GET
  @Path("/do_snapshot")
  public Response backup() throws Exception {
    snapshotBackup.execute();
    return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
  }

  @GET
  @Path("/incremental_backup")
  public Response backupIncrementals() throws Exception {
    scheduler.addTask("IncrementalBackup", IncrementalBackup.class, IncrementalBackup.getTimer());
    return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
  }

  @GET
  @Path("/restore")
  public Response restore(@QueryParam(REST_HEADER_RANGE) String daterange, @QueryParam(REST_HEADER_REGION) String region, @QueryParam(REST_HEADER_TOKEN) String token,
                          @QueryParam(REST_KEYSPACES) String keyspaces, @QueryParam(REST_RESTORE_PREFIX) String restorePrefix) throws Exception {
    Date startTime;
    Date endTime;

    if (StringUtils.isBlank(daterange) || daterange.equalsIgnoreCase("default")) {
      startTime = new DateTime().minusDays(1).toDate();
      endTime = new DateTime().toDate();
    } else {
      String[] restore = daterange.split(",");
      AbstractBackupPath path = pathProvider.get();
      startTime = path.parseDate(restore[0]);
      endTime = path.parseDate(restore[1]);
    }

    String origRestorePrefix = config.getRestorePrefix();
    if (StringUtils.isNotBlank(restorePrefix)) {
      config.setRestorePrefix(restorePrefix);
    }

    logger.info("Parameters: { token: [" + token + "], region: [" + region + "], startTime: [" + startTime + "], endTime: [" + endTime +
        "], keyspaces: [" + keyspaces + "], restorePrefix: [" + restorePrefix + "]}");

    restore(token, region, startTime, endTime, keyspaces);

    //Since this call is probably never called in parallel, config is multi-thread safe to be edited
    if (origRestorePrefix != null)
      config.setRestorePrefix(origRestorePrefix);
    else config.setRestorePrefix("");

    return Response.ok(REST_SUCCESS, MediaType.APPLICATION_JSON).build();
  }


  @GET
  @Path("/list")
  public Response list(@QueryParam(REST_HEADER_RANGE) String daterange, @QueryParam(REST_HEADER_FILTER) @DefaultValue("") String filter) throws Exception {
    Date startTime;
    Date endTime;

    if (StringUtils.isBlank(daterange) || daterange.equalsIgnoreCase("default")) {
      startTime = new DateTime().minusDays(1).toDate();
      endTime = new DateTime().toDate();
    } else {
      String[] restore = daterange.split(",");
      AbstractBackupPath path = pathProvider.get();
      startTime = path.parseDate(restore[0]);
      endTime = path.parseDate(restore[1]);
    }

    logger.info("Parameters: {backupPrefix: [" + config.getBackupPrefix() + "], daterange: [" + daterange + "], filter: [" + filter + "]}");

    Iterator<AbstractBackupPath> it = bkpStatusFs.list(config.getBackupPrefix(), startTime, endTime);
    JSONObject object = new JSONObject();
    object = constructJsonResponse(object, it, filter);
    return Response.ok(object.toString(2), MediaType.APPLICATION_JSON).build();
  }

  /**
   * Restore with the specified start and end time.
   *
   * @param token     Overrides the current token with this one, if specified
   * @param region    Override the region for searching backup
   * @param startTime Start time
   * @param endTime   End time upto which the restore should fetch data
   * @param keyspaces Comma seperated list of keyspaces to restore
   * @throws Exception
   */
  private void restore(String token, String region, Date startTime, Date endTime, String keyspaces) throws Exception {
    String origRegion = config.getDC();
    String origToken = priamServer.getId().getInstance().getToken();
    if (StringUtils.isNotBlank(token))
      priamServer.getId().getInstance().setToken(token);

    if (config.isRestoreClosestToken())
      priamServer.getId().getInstance().setToken(closestToken(priamServer.getId().getInstance().getToken(), config.getDC()));

    if (StringUtils.isNotBlank(region)) {
      config.setDC(region);
      logger.info("Restoring from region " + region);
      priamServer.getId().getInstance().setToken(closestToken(priamServer.getId().getInstance().getToken(), region));
      logger.info("Restore will use token " + priamServer.getId().getInstance().getToken());
    }

    setRestoreKeyspaces(keyspaces);

    try {
      restoreObj.restore(startTime, endTime);
    } finally {
      config.setDC(origRegion);
      priamServer.getId().getInstance().setToken(origToken);
    }
    tuner.updateAutoBootstrap(config.getYamlLocation(), false);
    cassProcess.start(true);
  }

  /**
   * Find closest token in the specified region
   */
  private String closestToken(String token, String region) {
    List<PriamInstance> plist = factory.getAllIds(config.getAppName());
    List<BigInteger> tokenList = Lists.newArrayList();
    for (PriamInstance ins : plist) {
      if (ins.getDC().equalsIgnoreCase(region))
        tokenList.add(new BigInteger(ins.getToken()));
    }
    return tokenManager.findClosestToken(new BigInteger(token), tokenList).toString();
  }

  /*
   * TODO: decouple the servlet, config, and restorer. this should not rely on a side
   *       effect of a list mutation on the config object (treating it as global var).
   */
  private void setRestoreKeyspaces(String keyspaces) {
    if (StringUtils.isNotBlank(keyspaces)) {
      List<String> newKeyspaces = Lists.newArrayList(keyspaces.split(","));
      config.setRestoreKeySpaces(newKeyspaces);
    }
  }

  private JSONObject constructJsonResponse(JSONObject object, Iterator<AbstractBackupPath> it, String filter) throws Exception {
    int fileCnt = 0;
    filter = filter.contains("?") ? filter.substring(0, filter.indexOf("?")) : filter;

    try {
      JSONArray jArray = new JSONArray();
      while (it.hasNext()) {
        AbstractBackupPath p = it.next();
        if (!filter.isEmpty() && BackupFileType.valueOf(filter) != p.getType())
          continue;
        JSONObject backupJSON = new JSONObject();
        backupJSON.put("bucket", config.getBackupPrefix());
        backupJSON.put("filename", p.getRemotePath());
        backupJSON.put("app", p.getClusterName());
        backupJSON.put("region", p.getRegion());
        backupJSON.put("token", p.getToken());
        backupJSON.put("ts", new DateTime(p.getTime()).toString(FMT));
        backupJSON.put("instance_id", p.getInstanceIdentity()
            .getInstance().getInstanceId());
        backupJSON.put("uploaded_ts",
            new DateTime(p.getUploadedTs()).toString(FMT));
        if ("meta".equalsIgnoreCase(filter)) {
          List<AbstractBackupPath> allFiles = metaData.get(p);
          long totalSize = 0;
          for (AbstractBackupPath abp : allFiles)
            totalSize = totalSize + abp.getSize();
          backupJSON.put("num_files", Long.toString(allFiles.size()));
          // keyValues.put("TOTAL-SIZE", Long.toString(totalSize)); //
          // Add Later
        }
        fileCnt++;
        jArray.put(backupJSON);
      }
      object.put("files", jArray);
      object.put("num_files", fileCnt);
    } catch (JSONException jse) {
      logger.info("Caught JSON Exception --> " + jse.getMessage());
    }
    return object;
  }

  public void removeAllDataFiles(String ks) throws Exception {
    String cleanupDirPath = config.getDataFileLocation() + File.separator + ks;
    logger.info("Starting to clean all the files inside <" + cleanupDirPath + ">");
    SystemUtils.cleanupDir(cleanupDirPath, null);
    logger.info("*** Done cleaning all the files inside <" + cleanupDirPath + ">");
  }

}
