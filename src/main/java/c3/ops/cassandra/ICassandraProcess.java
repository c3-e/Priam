package c3.ops.cassandra;

import c3.ops.cassandra.defaultimpl.CassandraProcessManager;
import com.google.inject.ImplementedBy;

import java.io.IOException;

/**
 * Interface to aid in starting and stopping cassandra.
 *
 * @author jason brown
 */
@ImplementedBy(CassandraProcessManager.class)
public interface ICassandraProcess {
	void start(boolean join_ring) throws IOException;

	void stop() throws IOException;
}
