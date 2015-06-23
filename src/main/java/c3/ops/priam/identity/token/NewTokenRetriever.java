package c3.ops.priam.identity.token;

import c3.ops.priam.IConfiguration;
import c3.ops.priam.identity.IMembership;
import c3.ops.priam.identity.IPriamInstanceFactory;
import c3.ops.priam.identity.PriamInstance;
import c3.ops.priam.utils.ITokenManager;
import c3.ops.priam.utils.Sleeper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ListMultimap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

public class NewTokenRetriever extends TokenRetrieverBase implements INewTokenRetriever {

  private static final Logger logger = LoggerFactory.getLogger(NewTokenRetriever.class);
  private IPriamInstanceFactory<PriamInstance> factory;
  private IMembership membership;
  private IConfiguration config;
  private Sleeper sleeper;
  private ITokenManager tokenManager;
  private ListMultimap<String, PriamInstance> locMap;

  @Inject
  //Note: do not parameterized the generic type variable to an implementation as it confuses Guice in the binding.
  public NewTokenRetriever(IPriamInstanceFactory factory, IMembership membership, IConfiguration config, Sleeper sleeper, ITokenManager tokenManager) {
    this.factory = factory;
    this.membership = membership;
    this.config = config;
    this.sleeper = sleeper;
    this.tokenManager = tokenManager;
  }

  @Override
  public PriamInstance get() throws Exception {

    logger.info("Generating my own and new token");
    // Sleep random interval - upto 15 sec
    sleeper.sleep(new Random().nextInt(15000));
    int hash = tokenManager.regionOffset(config.getDC());
    // use this hash so that the nodes are spred far away from the other
    // regions.

    int max = hash;
    List<PriamInstance> allInstances = factory.getAllIds(config.getAppName());
    for (PriamInstance data : allInstances)
      max = (data.getRac().equals(config.getRac()) && (data.getId() > max)) ? data.getId() : max;
    int maxSlot = max - hash;
    int my_slot = 0;

    if (hash == max && locMap.get(config.getRac()).size() == 0) {
      int idx = config.getRacs().indexOf(config.getRac());
      Preconditions.checkState(idx >= 0, "Rac %s is not in Racs %s", config.getRac(), config.getRacs());
      my_slot = idx + maxSlot;
    } else
      my_slot = config.getRacs().size() + maxSlot;

    logger.info(String.format("Trying to createToken with slot %d with rac count %d with rac membership size %d with dc %s",
        my_slot, membership.getRacCount(), membership.getRacMembershipSize(), config.getDC()));
    //String payload = tokenManager.createToken(my_slot, membership.getRacCount(), membership.getRacMembershipSize(), config.getDC());
    // Get token from cassandra.yaml
    String yamlToken = String.valueOf(config.getCassYamlProperty("initial_token"));
    return factory.create(config.getAppName(), my_slot + hash, config.getInstanceName(), config.getHostname(), config.getHostIP(), config.getRac(), null, yamlToken);

  }

  /*
   * @param A map of the rac for each instance.
   */
  @Override
  public void setLocMap(ListMultimap<String, PriamInstance> locMap) {
    this.locMap = locMap;
  }

}
