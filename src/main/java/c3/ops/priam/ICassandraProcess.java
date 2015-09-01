package c3.ops.priam;

import c3.ops.priam.defaultimpl.CassandraProcessManager;
import com.google.inject.ImplementedBy;

import java.io.IOException;

/**
 * Interface to aid in starting and stopping cassandra.
 */
@ImplementedBy(CassandraProcessManager.class)
public interface ICassandraProcess {
  void start(boolean join_ring) throws IOException;

  void stop() throws IOException;

  boolean status() throws IOException;
}
