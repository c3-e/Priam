package c3.ops.priam.identity.token;

import c3.ops.priam.IConfiguration;
import c3.ops.priam.identity.IMembership;
import c3.ops.priam.identity.IPriamInstanceFactory;
import c3.ops.priam.identity.PriamInstance;
import c3.ops.priam.utils.Sleeper;
import com.google.common.collect.ListMultimap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

public class PreGeneratedTokenRetriever extends TokenRetrieverBase implements IPreGeneratedTokenRetriever {

  private static final Logger logger = LoggerFactory.getLogger(PreGeneratedTokenRetriever.class);
  private IPriamInstanceFactory factory;
  private IMembership membership;
  private IConfiguration config;
  private Sleeper sleeper;
  private ListMultimap<String, PriamInstance> locMap;

  @Inject
  public PreGeneratedTokenRetriever(IPriamInstanceFactory factory, IMembership membership, IConfiguration config, Sleeper sleeper) {
    this.factory = factory;
    this.membership = membership;
    this.config = config;
    this.sleeper = sleeper;
  }

  @Override
  public PriamInstance get() throws Exception {
    logger.info("Looking for any pre-generated token");

    final List<PriamInstance> allIds = factory.getAllIds(config.getAppName());
    List<String> ringInstances = membership.getRacMembership();
    // Sleep random interval - upto 15 sec
    sleeper.sleep(new Random().nextInt(5000) + 10000);
    for (PriamInstance dead : allIds) {
      // test same zone and is it is alive.
      if (!dead.getRac().equals(config.getRac()) || ringInstances.contains(dead.getInstanceId()) || !isInstanceDummy(dead))
        continue;
      logger.info("Found pre-generated token: " + dead.getToken());
      PriamInstance markAsDead = factory.create(dead.getApp() + "-dead", dead.getId(), dead.getInstanceId(), dead.getHostName(), dead.getHostIP(), dead.getRac(), dead.getVolumes(),
          dead.getToken());
      // remove it as we marked it down...
      factory.delete(dead);


      String payLoad = markAsDead.getToken();
      logger.info("Trying to grab slot {} with availability zone {}", markAsDead.getId(), markAsDead.getRac());
      return factory.create(config.getAppName(), markAsDead.getId(), config.getInstanceName(), config.getHostname(), config.getHostIP(), config.getRac(), markAsDead.getVolumes(), payLoad);
    }
    return null;
  }

  @Override
  public void setLocMap(ListMultimap<String, PriamInstance> locMap) {
    this.locMap = locMap;

  }

}
