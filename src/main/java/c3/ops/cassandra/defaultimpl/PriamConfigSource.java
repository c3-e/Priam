package c3.ops.cassandra.defaultimpl;

import c3.ops.cassandra.CompositeConfigSource;
import c3.ops.cassandra.PropertiesConfigSource;
import c3.ops.cassandra.SimpleDBConfigSource;
import c3.ops.cassandra.SystemPropertiesConfigSource;

import javax.inject.Inject;

/**
 * Default {@link c3.ops.cassandra.IConfigSource} pulling in configs from SimpleDB, local Properties, and System Properties.
 */
public class PriamConfigSource extends CompositeConfigSource {

	@Inject
	public PriamConfigSource(final SimpleDBConfigSource simpleDBConfigSource,
	                         final PropertiesConfigSource propertiesConfigSource,
	                         final SystemPropertiesConfigSource systemPropertiesConfigSource) {
		// this order was based off PriamConfigurations loading.  W/e loaded last could override, but with Composite, first
		// has the highest priority.
		super(simpleDBConfigSource,
				propertiesConfigSource,
				systemPropertiesConfigSource);
	}
}
