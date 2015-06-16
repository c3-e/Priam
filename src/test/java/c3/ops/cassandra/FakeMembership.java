package c3.ops.cassandra;

import c3.ops.cassandra.identity.IMembership;

import java.util.Collection;
import java.util.List;

public class FakeMembership implements IMembership {

	private List<String> instances;

	public FakeMembership(List<String> priamInstances) {
		this.instances = priamInstances;
	}

	public void setInstances(List<String> priamInstances) {
		this.instances = priamInstances;
	}

	@Override
	public List<String> getRacMembership() {
		return instances;
	}

	@Override
	public int getRacMembershipSize() {
		return 3;
	}

	@Override
	public int getRacCount() {
		return 3;
	}

	@Override
	public void addACL(Collection<String> listIPs, int from, int to) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeACL(Collection<String> listIPs, int from, int to) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<String> listACL(int from, int to) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void expandRacMembership(int count) {
		// TODO Auto-generated method stub

	}
}
