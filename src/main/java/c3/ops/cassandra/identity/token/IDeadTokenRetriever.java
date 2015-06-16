package c3.ops.cassandra.identity.token;

import c3.ops.cassandra.identity.PriamInstance;
import com.google.common.collect.ListMultimap;

public interface IDeadTokenRetriever {

	public PriamInstance get() throws Exception;

	/*
	 * @return the IP address of the dead instance to which we will acquire its token
	 */
	public String getReplaceIp();

	/*
	 * @param A map of the rac for each instance.
	 */
	public void setLocMap(ListMultimap<String, PriamInstance> locMap);
}
