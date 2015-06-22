package c3.ops.priam.identity.token;

import c3.ops.priam.identity.PriamInstance;
import com.google.common.collect.ListMultimap;

public interface INewTokenRetriever {

  public PriamInstance get() throws Exception;

  /*
   * @param A map of the rac for each instance.
   */
  public void setLocMap(ListMultimap<String, PriamInstance> locMap);
}
