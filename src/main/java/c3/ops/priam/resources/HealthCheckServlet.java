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
import c3.ops.priam.backup.AbstractBackupPath;
import c3.ops.priam.backup.IBackupFileSystem;
import c3.ops.priam.backup.Restore;
import c3.ops.priam.backup.SnapshotBackup;
import c3.ops.priam.identity.IPriamInstanceFactory;
import c3.ops.priam.utils.CassandraTuner;
import c3.ops.priam.utils.ITokenManager;
import c3.ops.priam.utils.SystemUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Path("/v1/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthCheckServlet {
  private static final Logger logger = LoggerFactory.getLogger(HealthCheckServlet.class);

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
  public HealthCheckServlet(PriamServer priamServer, IConfiguration config, @Named("backup") IBackupFileSystem backupFs, @Named("backup_status") IBackupFileSystem bkpStatusFs, Restore restoreObj, Provider<AbstractBackupPath> pathProvider, CassandraTuner tuner,
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

  @POST
  @Path("/1")
  public Response status() throws Exception {
    int restoreTCount = restoreObj.getActiveCount();
    logger.debug("Thread counts for backup is: %d", restoreTCount);
    int backupTCount = backupFs.getActivecount();
    logger.debug("Thread counts for restore is: %d", backupTCount);
    JSONObject object = new JSONObject();
    object.put("Restore", new Integer(restoreTCount));
    object.put("Status", restoreObj.state().toString());
    object.put("Backup", new Integer(backupTCount));
    object.put("Status", snapshotBackup.state().toString());
    return Response.ok(object.toString(), MediaType.APPLICATION_JSON).build();
  }

  @POST
  @Path("disk_report")
  public Response diskSpaceReport() throws JSONException {
    File cass = new File(config.getDataFileLocation());

    JSONObject drObj = new JSONObject();
    drObj.put("totalSpace", cass.getTotalSpace());
    drObj.put("freeSpace", cass.getFreeSpace());

    return Response.ok(drObj, MediaType.APPLICATION_JSON).build();
  }

  @POST
  @Path("java_version")
  public Response javaVersion() throws JSONException {
    return Response.ok(new JSONObject().put("version",System.getProperty("java.version")), MediaType.APPLICATION_JSON).build();
  }
}
