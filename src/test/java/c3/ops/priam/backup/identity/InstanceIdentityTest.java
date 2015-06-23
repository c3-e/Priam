package c3.ops.priam.backup.identity;

import c3.ops.priam.identity.PriamInstance;
import c3.ops.priam.utils.ITokenManager;
import c3.ops.priam.utils.TokenManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InstanceIdentityTest extends InstanceTestUtils {
  private static final ITokenManager tokenManager = new TokenManager();

  @Test
  public void testCreateToken() throws Exception {

    identity = createInstanceIdentity("az1", "fakeinstance1");
    int hash = tokenManager.regionOffset(config.getDC());
    assertEquals(0, identity.getInstance().getId() - hash);

    identity = createInstanceIdentity("az1", "fakeinstance2");
    assertEquals(3, identity.getInstance().getId() - hash);

    identity = createInstanceIdentity("az1", "fakeinstance3");
    assertEquals(6, identity.getInstance().getId() - hash);

    // try next region
    identity = createInstanceIdentity("az2", "fakeinstance4");
    assertEquals(1, identity.getInstance().getId() - hash);

    identity = createInstanceIdentity("az2", "fakeinstance5");
    assertEquals(4, identity.getInstance().getId() - hash);

    identity = createInstanceIdentity("az2", "fakeinstance6");
    assertEquals(7, identity.getInstance().getId() - hash);

    // next
    identity = createInstanceIdentity("az3", "fakeinstance7");
    assertEquals(2, identity.getInstance().getId() - hash);

    identity = createInstanceIdentity("az3", "fakeinstance8");
    assertEquals(5, identity.getInstance().getId() - hash);

    identity = createInstanceIdentity("az3", "fakeinstance9");
    assertEquals(8, identity.getInstance().getId() - hash);
  }

  @Test
  public void testGetSeeds() throws Exception {
    createInstances();
    identity = createInstanceIdentity("az1", "fakeinstance1");
    assertEquals(3, identity.getSeeds().size());
  }


  public void printInstance(PriamInstance ins, int hash) {
    System.out.println("ID: " + (ins.getId() - hash));
    System.out.println("PayLoad: " + ins.getToken());

  }

}
