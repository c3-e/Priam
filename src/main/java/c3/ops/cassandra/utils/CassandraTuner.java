package c3.ops.cassandra.utils;

import c3.ops.cassandra.defaultimpl.StandardTuner;
import com.google.inject.ImplementedBy;

import java.io.IOException;

@ImplementedBy(StandardTuner.class)
public interface CassandraTuner {
	void writeAllProperties(String yamlLocation, String hostname, String seedProvider) throws IOException;

	void updateAutoBootstrap(String yamlLocation, boolean autobootstrap) throws IOException;
}
