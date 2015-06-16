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
package c3.ops.cassandra.aws;

import c3.ops.cassandra.IConfiguration;
import c3.ops.cassandra.identity.IMembership;
import c3.ops.cassandra.identity.IPriamInstanceFactory;
import c3.ops.cassandra.identity.InstanceIdentity;
import c3.ops.cassandra.identity.PriamInstance;
import c3.ops.cassandra.scheduler.SimpleTimer;
import c3.ops.cassandra.scheduler.Task;
import c3.ops.cassandra.scheduler.TaskTimer;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;
import java.util.Random;

/**
 * this class will associate an Public IP's with a new instance so they can talk
 * across the regions.
 * <p/>
 * Requirement: 1) Nodes in the same region needs to be able to talk to each
 * other. 2) Nodes in other regions needs to be able to talk to the others in
 * the other region.
 * <p/>
 * Assumption: 1) IPriamInstanceFactory will provide the membership... and will
 * be visible across the regions 2) IMembership amazon or any other
 * implementation which can tell if the instance is part of the group (Ring in
 * amazons case).
 */
@Singleton
public class UpdateSecuritySettings extends Task {
	public static final String JOBNAME = "Update_SG";
	private static final Random ran = new Random();
	public static boolean firstTimeUpdated = false;
	private final IMembership membership;
	private final IPriamInstanceFactory<PriamInstance> factory;

	@Inject
	//Note: do not parameterized the generic type variable to an implementation as it confuses Guice in the binding.
	public UpdateSecuritySettings(IConfiguration config, IMembership membership, IPriamInstanceFactory factory) {
		super(config);
		this.membership = membership;
		this.factory = factory;
	}

	public static TaskTimer getTimer(InstanceIdentity id) {
		SimpleTimer return_;
		if (id.isSeed())
			return_ = new SimpleTimer(JOBNAME, 120 * 1000 + ran.nextInt(120 * 1000));
		else
			return_ = new SimpleTimer(JOBNAME);
		return return_;
	}

	/**
	 * Seeds nodes execute this at the specifed interval.
	 * Other nodes run only on startup.
	 * Seeds in cassandra are the first node in each Availablity Zone.
	 */
	@Override
	public void execute() {
		// if seed dont execute.
		int port = config.getSSLStoragePort();
		List<String> acls = membership.listACL(port, port);
		List<PriamInstance> instances = factory.getAllIds(config.getAppName());

		// iterate to add...
		List<String> add = Lists.newArrayList();
		List<PriamInstance> allInstances = factory.getAllIds(config.getAppName());
		for (PriamInstance instance : allInstances) {
			String range = instance.getHostIP() + "/32";
			if (!acls.contains(range))
				add.add(range);
		}
		if (add.size() > 0) {
			membership.addACL(add, port, port);
			firstTimeUpdated = true;
		}

		// just iterate to generate ranges.
		List<String> currentRanges = Lists.newArrayList();
		for (PriamInstance instance : instances) {
			String range = instance.getHostIP() + "/32";
			currentRanges.add(range);
		}

		// iterate to remove...
		List<String> remove = Lists.newArrayList();
		for (String acl : acls)
			if (!currentRanges.contains(acl)) // if not found then remove....
				remove.add(acl);
		if (remove.size() > 0) {
			membership.removeACL(remove, port, port);
			firstTimeUpdated = true;
		}
	}

	@Override
	public String getName() {
		return JOBNAME;
	}
}
