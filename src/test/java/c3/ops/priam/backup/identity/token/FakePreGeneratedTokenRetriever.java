package c3.ops.priam.backup.identity.token;

import c3.ops.priam.identity.PriamInstance;
import c3.ops.priam.identity.token.IPreGeneratedTokenRetriever;
import com.google.common.collect.ListMultimap;

public class FakePreGeneratedTokenRetriever implements
    IPreGeneratedTokenRetriever {

  @Override
  public PriamInstance get() throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setLocMap(ListMultimap<String, PriamInstance> locMap) {
    // TODO Auto-generated method stub

  }

}
