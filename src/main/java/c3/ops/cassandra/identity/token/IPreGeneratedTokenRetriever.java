package c3.ops.cassandra.identity.token;

import c3.ops.cassandra.identity.PriamInstance;
import com.google.common.collect.ListMultimap;

public interface IPreGeneratedTokenRetriever {

	public PriamInstance get() throws Exception;

	/*
	 * @param A map of the rac for each instance.
	 */
	public void setLocMap(ListMultimap<String, PriamInstance> locMap);
}
