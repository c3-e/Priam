package c3.ops.priam.aws;

import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import c3.ops.priam.IConfiguration;
import c3.ops.priam.identity.IMembership;
import c3.ops.priam.identity.IPriamInstanceFactory;
import c3.ops.priam.identity.InstanceIdentity;
import c3.ops.priam.identity.PriamInstance;
import c3.ops.priam.scheduler.SimpleTimer;
import c3.ops.priam.scheduler.Task;
import c3.ops.priam.scheduler.TaskTimer;

@Singleton
public class UpdateSecuritySettings extends Task
{
  public static final String JOBNAME = "Update_SG";
  public static boolean firstTimeUpdated = false;

  private static final Random ran = new Random();
  private final IMembership membership;
  private final IPriamInstanceFactory<PriamInstance> factory;

  @Inject
  //Note: do not parameterized the generic type variable to an implementation as it confuses Guice in the binding.
  public UpdateSecuritySettings(IConfiguration config, IMembership membership, IPriamInstanceFactory factory)
  {
    super(config);
    this.membership = membership;
    this.factory = factory;
  }

  /**
   * Seeds nodes execute this at the specifed interval.
   * Other nodes run only on startup.
   * Seeds in cassandra are the first node in each Availablity Zone.
   */
  @Override
  public void execute()
  {
    // if seed dont execute.
    int port = config.getSSLStoragePort();
    List<String> acls = membership.listACL(port, port);
    List<PriamInstance> instances = factory.getAllIds(config.getAppName());

    // iterate to add...
    List<String> add = Lists.newArrayList();
    List<PriamInstance> allInstances = factory.getAllIds(config.getAppName());
    for (PriamInstance instance : allInstances)
    {
      String range = instance.getHostIP() + "/32";
      if (!acls.contains(range))
        add.add(range);
    }
    if (add.size() > 0)
    {
      membership.addACL(add, port, port);
      firstTimeUpdated = true;
    }

    // just iterate to generate ranges.
    List<String> currentRanges = Lists.newArrayList();
    for (PriamInstance instance : instances)
    {
      String range = instance.getHostIP() + "/32";
      currentRanges.add(range);
    }

    // iterate to remove...
    List<String> remove = Lists.newArrayList();
    for (String acl : acls)
      if (!currentRanges.contains(acl)) // if not found then remove....
        remove.add(acl);
    if (remove.size() > 0)
    {
      membership.removeACL(remove, port, port);
      firstTimeUpdated = true;
    }
  }

  public static TaskTimer getTimer(InstanceIdentity id)
  {
    SimpleTimer return_;
    if (id.isSeed())
      return_ = new SimpleTimer(JOBNAME, 120 * 1000 + ran.nextInt(120 * 1000));
    else
      return_ = new SimpleTimer(JOBNAME);
    return return_;
  }

  @Override
  public String getName()
  {
    return JOBNAME;
  }
}
