package c3.ops.priam.defaultimpl;

import c3.ops.priam.IConfigSource;
import c3.ops.priam.IConfiguration;
import c3.ops.priam.ICredential;
import c3.ops.priam.identity.config.AwsVpcInstanceDataRetriever;
import c3.ops.priam.utils.RetryableCallable;
import c3.ops.priam.utils.SystemUtils;
import c3.ops.priam.identity.config.InstanceDataRetriever;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class PriamConfiguration implements IConfiguration {

   public static final String PRIAM_PRE = "priam";
   private static final Logger logger = LoggerFactory.getLogger(PriamConfiguration.class);

   private String NETWORK_VPC, INSTANCE_ID, RAC, INSTANCE_TYPE, LOCAL_IP, REGION, RING_NAME, C3_HOSTNAME;

   private final IConfigSource config;
   private final ICredential provider;

   private List<String> DEFAULT_AVAILABILITY_ZONES = ImmutableList.of();
   private Map YAML_PROPERTY_MAP;

   @Inject
   public PriamConfiguration(ICredential provider, IConfigSource config) {
      this.provider = provider;
      this.config = config;
   }

   @Override
   public void intialize() {
      InstanceDataRetriever instanceDataRetriever;
      try {
         instanceDataRetriever = new AwsVpcInstanceDataRetriever();
      } catch (Exception e) {
         throw new IllegalStateException(
               "Exception when instantiating the instance data retriever.  Msg: " + e.getLocalizedMessage());
      }

      try {
         REGION = instanceDataRetriever.getRegion();
      } catch (JSONException e) {
         throw new IllegalStateException(
               "Exception when instantiating region.  Msg: " + e.getLocalizedMessage());
      }

      RAC = instanceDataRetriever.getRac();
      INSTANCE_ID = instanceDataRetriever.getInstanceId();
      INSTANCE_TYPE = instanceDataRetriever.getInstanceType();
      NETWORK_VPC = instanceDataRetriever.getVpcId();
      LOCAL_IP = instanceDataRetriever.getPrivateIP();
      C3_HOSTNAME = SystemUtils.executeCommand("/bin/hostname");

      setupEnvVars();
      this.config.intialize(RING_NAME, REGION);
      setDefaultRACList(REGION);
      populateProps();
      populateYamlPropertyMap();
      SystemUtils.createDirs(getBackupCommitLogLocation());
      SystemUtils.createDirs(getCommitLogLocation());
      SystemUtils.createDirs(getCacheLocation());
      SystemUtils.createDirs(getDataFileLocation());
   }

   private void setupEnvVars() {
      if (StringUtils.isBlank(RING_NAME))
         RING_NAME = populateRingName(REGION, INSTANCE_ID);
      logger.info(String.format("REGION set to %s, Ring Name set to %s", REGION, RING_NAME));
   }

   /**
    * Query amazon to get Ring name. Currently not available as part of instance
    * info api.
    */
   private String populateRingName(String region, String instanceId) {
      GetRingName getRingName = new GetRingName(region, instanceId);

      try {
         return getRingName.call();
      } catch (Exception e) {
         logger.error("Failed to determine Ring name.", e);
         return null;
      }
   }

   /**
    * Populate yaml file properties
    */
   private void populateYamlPropertyMap() {
      DumperOptions options = new DumperOptions();
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      Yaml yaml = new Yaml(options);
      try {
         this.YAML_PROPERTY_MAP = (Map) yaml.load(new FileInputStream(this.getYamlLocation()));
      } catch (Exception e) {
         logger.info(e.getMessage());
      }
   }

   /**
    * Get the fist 3 available zones in the region
    */
   private void setDefaultRACList(String region) {
      AmazonEC2 client = new AmazonEC2Client(provider.getAwsCredentialProvider());
      client.setEndpoint("ec2." + region + ".amazonaws.com");
      DescribeAvailabilityZonesResult res = client.describeAvailabilityZones();
      List<String> zone = Lists.newArrayList();
      for (AvailabilityZone reg : res.getAvailabilityZones()) {
         if (reg.getState().equals("available"))
            zone.add(reg.getZoneName());
      }
      DEFAULT_AVAILABILITY_ZONES = ImmutableList.copyOf(zone);
   }

   private void populateProps() {
      config.set(PRIAM_PRE + ".az.ringname", RING_NAME);
      config.set(PRIAM_PRE + ".az.region", REGION);
   }

   @Override
   public String getCassStartupScript() {
      return config.get(PRIAM_PRE + ".cass.startscript", "/etc/init.d/cassandra start");
   }

   @Override
   public String getCassStopScript() {
      return config.get(PRIAM_PRE + ".cass.stopscript", "/etc/init.d/cassandra stop");
   }

   @Override
   public String getCassHome() {
      return config.get(PRIAM_PRE + ".cass.home", "/etc/cassandra");
   }

   @Override
   public String getBackupLocation() {
      return config.get(PRIAM_PRE + ".s3.base_dir", "backup");
   }

   @Override
   public String getBackupPrefix() {
      return config.get(PRIAM_PRE + ".s3.bucket", "cassandra-archive");
   }

   @Override
   public int getBackupRetentionDays() {
      return config.get(PRIAM_PRE + ".backup.retention", 5);
   }

   @Override
   public List<String> getBackupRacs() {
      return config.getList(PRIAM_PRE + ".backup.racs");
   }

   @Override
   public String getRestorePrefix() {
      return config.get(PRIAM_PRE + ".restore.prefix");
   }

   @Override
   public void setRestorePrefix(String prefix) {
      config.set(PRIAM_PRE + ".restore.prefix", prefix);

   }

   @Override
   public List<String> getRestoreKeySpaces() {
      return config.getList(PRIAM_PRE + ".restore.keyspaces");
   }

   @Override
   public void setRestoreKeySpaces(List<String> keyspaces) {
      if (keyspaces == null)
         return;

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < keyspaces.size(); i++) {
         if (i > 0)
            sb.append(",");

         sb.append(keyspaces.get(i));
      }

      config.set(PRIAM_PRE + ".restore.keyspaces", sb.toString());
   }

   @Override
   public String getDataFileLocation() {
      return config.get(PRIAM_PRE + ".data.location", "/opt/cass/data");
   }

   @Override
   public String getCacheLocation() {
      return config.get(PRIAM_PRE + ".cache.location", "/opt/cass/saved_caches");
   }

   @Override
   public String getCommitLogLocation() {
      return config.get(PRIAM_PRE + ".commitlog.location", "/opt/cass/commitlog");
   }

   @Override
   public String getBackupCommitLogLocation() {
      return config.get(PRIAM_PRE + ".backup.commitlog.location", "");
   }

   @Override
   public long getBackupChunkSize() {
      long size = config.get(PRIAM_PRE + ".backup.chunksizemb", 10);
      return size * 1024 * 1024L;
   }

   @Override
   public boolean isCommitLogBackup() {
      return config.get(PRIAM_PRE + ".backup.commitlog.enable", false);
   }

   @Override
   public int getJmxPort() {
      return config.get(PRIAM_PRE + ".jmx.port", 7199);
   }

   public int getNativeTransportPort() {
      return config.get(PRIAM_PRE + ".nativeTransport.port", 9042);
   }

   @Override
   public int getThriftPort() {
      return config.get(PRIAM_PRE + ".thrift.port", 9160);
   }

   @Override
   public int getStoragePort() {
      return config.get(PRIAM_PRE + ".storage.port", 7000);
   }

   @Override
   public int getSSLStoragePort() {
      return config.get(PRIAM_PRE + ".ssl.storage.port", 7001);
   }

   @Override
   public String getSnitch() {
      return config.get(PRIAM_PRE + ".endpoint_snitch", "org.apache.cassandra.locator.Ec2Snitch");
   }

   @Override
   public String getAppName() {
      return config.get(PRIAM_PRE + ".clustername", "cass_cluster");
   }

   @Override
   public String getRac() {
      return RAC;
   }

   @Override
   public List<String> getRacs() {
      return config.getList(PRIAM_PRE + ".zones.available", DEFAULT_AVAILABILITY_ZONES);
   }

   @Override
   public String getHostname() {
      return C3_HOSTNAME;
   }

   @Override
   public String getInstanceName() {
      return INSTANCE_ID;
   }

   @Override
   public String getHeapSize() {
      return config.get(PRIAM_PRE + ".heap.size." + INSTANCE_TYPE, "8G");
   }

   @Override
   public String getHeapNewSize() {
      return config.get(PRIAM_PRE + ".heap.newgen.size." + INSTANCE_TYPE, "2G");
   }

   @Override
   public String getMaxDirectMemory() {
      return config.get(PRIAM_PRE + ".direct.memory.size." + INSTANCE_TYPE, "50G");
   }

   @Override
   public int getBackupHour() {
      return config.get(PRIAM_PRE + ".backup.hour", 12);
   }

   @Override
   public String getRestoreSnapshot() {
      return config.get(PRIAM_PRE + ".restore.snapshot", "");
   }

   @Override
   public String getDC() {
      return config.get(PRIAM_PRE + ".az.region", REGION);
   }

   @Override
   public void setDC(String region) {
      config.set(PRIAM_PRE + ".az.region", region);
   }

   @Override
   public boolean isMultiDC() {
      return config.get(PRIAM_PRE + ".multiregion.enable", false);
   }

   @Override
   public int getMaxBackupUploadThreads() {
      return config.get(PRIAM_PRE + ".backup.threads", 2);
   }

   @Override
   public int getMaxBackupDownloadThreads() {
      return config.get(PRIAM_PRE + ".restore.threads", 8);
   }

   @Override
   public boolean isRestoreClosestToken() {
      return config.get(PRIAM_PRE + ".restore.closesttoken", false);
   }

   @Override
   public String getRingName() {
      return config.get(PRIAM_PRE + ".az.ringname", "");
   }

   @Override
   public String getACLGroupName() {
      return config.get(PRIAM_PRE + ".acl.groupname", this.getAppName());
   }

   @Override
   public boolean isIncrBackup() {
      return config.get(PRIAM_PRE + ".backup.incremental.enable", true);
   }

   @Override
   public String getHostIP() {
      return LOCAL_IP;
   }

   @Override
   public int getUploadThrottle() {
      return config.get(PRIAM_PRE + ".upload.throttle", Integer.MAX_VALUE);
   }

   @Override
   public boolean isLocalBootstrapEnabled() {
      return config.get(PRIAM_PRE + ".localbootstrap.enable", false);
   }

   @Override
   public int getInMemoryCompactionLimit() {
      return config.get(PRIAM_PRE + ".memory.compaction.limit", 128);
   }

   @Override
   public int getCompactionThroughput() {
      return config.get(PRIAM_PRE + ".compaction.throughput", 8);
   }

   @Override
   public int getMaxHintWindowInMS() {
      return config.get(PRIAM_PRE + ".hint.window", 10800000);
   }

   public int getHintedHandoffThrottleKb() {
      return config.get(PRIAM_PRE + ".hints.throttleKb", 1024);
   }

   public int getMaxHintThreads() {
      return config.get(PRIAM_PRE + ".hints.maxThreads", 2);
   }

   @Override
   public String getBootClusterName() {
      return config.get(PRIAM_PRE + ".bootcluster", "");
   }

   @Override
   public String getSeedProviderName() {
      return config.get(PRIAM_PRE + ".seed.provider", "c3.ops.cassandra.cassandra.extensions.NFSeedProvider");
   }

   @Override
   /**
    * Defaults to 0, means dont set it in yaml
    */
   public int getMemtableTotalSpaceMB() {
      return config.get(PRIAM_PRE + ".memtabletotalspace", 1024);
   }

   @Override
   public int getStreamingThroughputMB() {
      return config.get(PRIAM_PRE + ".streaming.throughput.mb", 400);
   }

   @Override
   public boolean getMultithreadedCompaction() {
      return config.get(PRIAM_PRE + ".multithreaded.compaction", false);
   }

   public String getPartitioner() {
      return config.get(PRIAM_PRE + ".partitioner", "org.apache.cassandra.dht.RandomPartitioner");
   }

   public String getKeyCacheSizeInMB() {
      return config.get(PRIAM_PRE + ".keyCache.size");
   }

   public String getKeyCacheKeysToSave() {
      return config.get(PRIAM_PRE + ".keyCache.count");
   }

   public String getRowCacheSizeInMB() {
      return config.get(PRIAM_PRE + ".rowCache.size");
   }

   public String getRowCacheKeysToSave() {
      return config.get(PRIAM_PRE + ".rowCache.count");
   }

   @Override
   public String getCassProcessName() {
      return config.get(PRIAM_PRE + ".cass.process", "CassandraDaemon");
   }

   public int getNumTokens() {
      return config.get(PRIAM_PRE + ".vnodes.numTokens", 1);
   }

   public String getYamlLocation() {
      return config.get(PRIAM_PRE + ".yamlLocation", getCassHome() + "/conf/cassandra.yaml");
   }

   public String getAuthenticator() {
      return config.get(PRIAM_PRE + ".authenticator", "org.apache.cassandra.auth.AllowAllAuthenticator");
   }

   public String getAuthorizer() {
      return config.get(PRIAM_PRE + ".authorizer", "org.apache.cassandra.auth.AllowAllAuthorizer");
   }

   public String getTargetKSName() {
      return config.get(PRIAM_PRE + ".target.keyspace");
   }

   @Override
   public String getTargetCFName() {
      return config.get(PRIAM_PRE + ".target.columnfamily");
   }

   @Override
   public boolean doesCassandraStartManually() {
      return config.get(PRIAM_PRE + ".cass.manual.start.enable", false);
   }

   @Override
   public boolean doesCassandraConfiguredManually() {
      return config.get(PRIAM_PRE + ".cass.manual.config.enable", false);
   }

   public String getInternodeCompression() {
      return config.get(PRIAM_PRE + ".internodeCompression", "all");
   }

   @Override
   public boolean isBackingUpCommitLogs() {
      return config.get(PRIAM_PRE + ".clbackup.enabled", false);
   }

   @Override
   public String getCommitLogBackupArchiveCmd() {
      return config.get(PRIAM_PRE + ".clbackup.archiveCmd", "/bin/ln %path /mnt/data/backup/%name");
   }

   @Override
   public String getCommitLogBackupRestoreCmd() {
      return config.get(PRIAM_PRE + ".clbackup.restoreCmd", "/bin/mv %from %to");
   }

   @Override
   public String getCommitLogBackupRestoreFromDirs() {
      return config.get(PRIAM_PRE + ".clbackup.restoreDirs", "/mnt/data/backup/commitlog/");
   }

   @Override
   public String getCommitLogBackupRestorePointInTime() {
      return config.get(PRIAM_PRE + ".clbackup.restoreTime", "");
   }

   @Override
   public int maxCommitLogsRestore() {
      return config.get(PRIAM_PRE + ".clrestore.max", 10);
   }

   @Override
   public boolean isVpcRing() {
      return config.get(PRIAM_PRE + ".vpc", false);
   }

   public boolean isClientSslEnabled() {
      return config.get(PRIAM_PRE + ".client.sslEnabled", false);
   }

   public String getInternodeEncryption() {
      return config.get(PRIAM_PRE + ".internodeEncryption", "none");
   }

   public boolean isDynamicSnitchEnabled() {
      return config.get(PRIAM_PRE + ".dsnitchEnabled", true);
   }

   public boolean isThriftEnabled() {
      return config.get(PRIAM_PRE + ".thrift.enabled", true);
   }

   public boolean isNativeTransportEnabled() {
      return config.get(PRIAM_PRE + ".nativeTransport.enabled", false);
   }

   public String getS3EndPoint() {
      return "s3." + getDC() + ".amazonaws.com";
   }

   public int getConcurrentReadsCnt() {
      return config.get(PRIAM_PRE + ".concurrentReads", 32);
   }

   public int getConcurrentWritesCnt() {
      return config.get(PRIAM_PRE + ".concurrentWrites", 32);
   }

   public int getConcurrentCompactorsCnt() {
      int cpus = Runtime.getRuntime().availableProcessors();
      return config.get(PRIAM_PRE + ".concurrentCompactors", cpus);
   }

   public String getRpcServerType() {
      return config.get(PRIAM_PRE + ".rpc.server.type", "sync");
   }

   public int getIndexInterval() {
      return config.get(PRIAM_PRE + ".index.interval", 256);
   }

   public String getExtraConfigParams() {
      return config.get(PRIAM_PRE + ".extra.params");
   }

   public String getCassYamlVal(String priamKey) {
      return config.get(priamKey);
   }

   public boolean getAutoBoostrap() {
      return config.get(PRIAM_PRE + ".auto.bootstrap", true);
   }

   //values are cassandra, solr, hadoop, spark or hadoop-spark
   public String getDseClusterType() {
      return config.get(PRIAM_PRE + ".dse.cluster.type" + "." + RING_NAME, "cassandra");
   }

   @Override
   public boolean isCreateNewTokenEnable() {
      return config.get(PRIAM_PRE + ".create.new.token.enable", true);
   }

   @Override
   public Object getCassYamlProperty(String Key) {
      return this.YAML_PROPERTY_MAP.get(Key);
   }

   @Override
   public boolean isDebugBackupEnabled() {
      return config.get(PRIAM_PRE + ".restore.source.type", false);
   }

   @Override
   public boolean isValidateBackupEnabled() {
      return config.get(PRIAM_PRE + ".restore.source.type", false);
   }

   private class GetRingName extends RetryableCallable<String> {

      private static final int NUMBER_OF_RETRIES = 15;
      private static final long WAIT_TIME = 30000;
      private final String instanceId;
      private final AmazonEC2 client;

      public GetRingName(String region, String instanceId) {
         super(NUMBER_OF_RETRIES, WAIT_TIME);
         this.instanceId = instanceId;
         client = new AmazonEC2Client(provider.getAwsCredentialProvider());
         client.setEndpoint("ec2." + region + ".amazonaws.com");
      }

      @Override
      public String retriableCall() throws IllegalStateException {
         DescribeInstancesRequest desc = new DescribeInstancesRequest().withInstanceIds(instanceId);
         DescribeInstancesResult res = client.describeInstances(desc);
         String hostName = C3_HOSTNAME;

         for (Reservation resr : res.getReservations()) {
            for (Instance ins : resr.getInstances()) {
               for (com.amazonaws.services.ec2.model.Tag tag : ins.getTags()) {
                  if (tag.getKey().equals("Name") || tag.getKey().equals("QName") || tag.getKey().equals("host_name"))
                     hostName = tag.getValue();
               }
            }
         }

         Pattern pattern = Pattern.compile("^(\\w+\\-\\w+)\\-cass\\-\\d+.*+$");
         Matcher matcher = pattern.matcher(hostName);
         if (matcher.find())
            return matcher.group(1);

         logger.warn("Couldn't determine Ring name");
         throw new IllegalStateException("Couldn't determine Ring name");
      }
   }

   @Override
   public int getUncrementalBkupQueueSize() {
      return config.get(PRIAM_PRE + ".incremental.bkup.queue.size", 100000);
   }

   public int getRpcMinThreads() {
      return config.get(PRIAM_PRE + ".rpc.min.threads", 16);
   }

   public int getRpcMaxThreads() {
      return config.get(PRIAM_PRE + ".rpc.max.threads", 2048);
   }

   @Override
   public String getAWSRoleAssumptionArn() {
      return config.get(PRIAM_PRE + ".roleassumption.arn");
   }

   public Map<String, String> getExtraEnvParams() {

      String envParams = config.get(PRIAM_PRE + ".extra.env.params");
      if (envParams == null) {
         logger.info("getExtraEnvParams: No extra env params");
         return null;
      }
      Map<String, String> extraEnvParamsMap = new HashMap<String, String>();
      String[] pairs = envParams.split(",");
      logger.info("getExtraEnvParams: Extra cass params. From config :" + envParams);
      for (int i = 0; i < pairs.length; i++) {
         String[] pair = pairs[i].split("=");
         if (pair.length > 1) {
            String priamKey = pair[0];
            String cassKey = pair[1];
            String cassVal = config.get(priamKey);
            logger.info("getExtraEnvParams: Start-up/ env params: Priamkey[" + priamKey + "], CassStartupKey[" + cassKey
                        + "], Val[" + cassVal + "]");
            if (!StringUtils.isBlank(cassKey) && !StringUtils.isBlank(cassVal)) {
               extraEnvParamsMap.put(cassKey, cassVal);
            }
         }
      }
      return extraEnvParamsMap;

   }

   @Override
   public String getRestoreSourceType() {
      return config.get(PRIAM_PRE + ".restore.source.type");
   }

   @Override
   public boolean isEncryptBackupEnabled() {
      return config.get(PRIAM_PRE + ".encrypted.backup.enabled", false);
   }

   @Override
   public Boolean isIncrBackupParallelEnabled() {
      return config.get(PRIAM_PRE + ".incremental.bkup.parallel", false);
   }

   @Override
   public String getSnapshotKeyspaceFilters() {
      return config.get(PRIAM_PRE + ".snapshot.keyspace.filter");
   }

   @Override
   public String getSnapshotCFFilter() throws IllegalArgumentException {
      return config.get(PRIAM_PRE + ".snapshot.cf.filter");
   }

   @Override
   public String getVpcId() {
      return NETWORK_VPC;
   }

   @Override
   public String getRestoreKeyspaceFilter() {
      return config.get(PRIAM_PRE + ".restore.keyspace.filter");
   }

   @Override
   public String getIncrementalKeyspaceFilters() {
      return config.get(PRIAM_PRE + ".incremental.keyspace.filter");
   }

   @Override
   public String getIncrementalCFFilter() {
      return config.get(PRIAM_PRE + ".incremental.cf.filter");
   }

   @Override
   public String getRestoreCFFilter() {
      return config.get(PRIAM_PRE + ".restore.cf.filter");
   }

   @Override
   public int getIncrementalBkupMaxConsumers() {
      return config.get(PRIAM_PRE + ".incremental.bkup.max.consumers", 4);
   }

   @Override
   public String getPrivateKeyLocation() {
      return config.get(PRIAM_PRE + ".private.key.location");
   }
}
