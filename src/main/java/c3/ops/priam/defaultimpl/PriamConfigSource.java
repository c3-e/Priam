package c3.ops.priam.defaultimpl;

import c3.ops.priam.CompositeConfigSource;
import c3.ops.priam.PropertiesConfigSource;
import c3.ops.priam.SimpleDBConfigSource;
import c3.ops.priam.SystemPropertiesConfigSource;

import javax.inject.Inject;

/**
 * Default {@link c3.ops.priam.IConfigSource} pulling in configs from SimpleDB, local Properties, and System Properties.
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
