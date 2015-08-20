package c3.ops.priam;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Loads the 'Opsagent.properties' file as a source.
 */
public class PropertiesConfigSource extends AbstractConfigSource {
  private static final Logger logger = LoggerFactory.getLogger(PropertiesConfigSource.class.getName());

  private static final String DEFAULT_PRIAM_PROPERTIES = "opsagent.properties";

  private final Map<String, String> data = Maps.newConcurrentMap();
  private final String priamFile;

  public PropertiesConfigSource() {
    String userPriamFile = System.getProperty("opsagent.opsagentProperties");
    if (userPriamFile != null && new File(userPriamFile).exists()) {
      logger.info("Using opsagent.properties from {}.", userPriamFile);
      this.priamFile = userPriamFile;
    } else {
      this.priamFile = DEFAULT_PRIAM_PROPERTIES;
    }
  }

  public PropertiesConfigSource(final Properties properties) {
    checkNotNull(properties);
    this.priamFile = DEFAULT_PRIAM_PROPERTIES;
    clone(properties);
  }

  @VisibleForTesting
  PropertiesConfigSource(final String file) {
    this.priamFile = checkNotNull(file);
  }

  @Override
  public void intialize(final String ringName, final String region) {
    super.intialize(ringName, region);
    Properties properties = new Properties();
    URL url = PropertiesConfigSource.class.getClassLoader().getResource(priamFile);
    if (url != null) {
      try {
        properties.load(url.openStream());
        clone(properties);
      } catch (IOException e) {
        logger.info("No Opsagent.properties. Ignore!");
      }
    } else {
      logger.info("No Opsagent.properties. Ignore!");
    }
  }

  @Override
  public String get(final String prop) {
    return data.get(prop);
  }

  @Override
  public void set(final String key, final String value) {
    Preconditions.checkNotNull(value, "Value can not be null for configurations.");
    data.put(key, value);
  }


  @Override
  public int size() {
    return data.size();
  }

  @Override
  public boolean contains(final String prop) {
    return data.containsKey(prop);
  }

  /**
   * Clones all the values from the properties.  If the value is null, it will be ignored.
   *
   * @param properties to clone
   */
  private void clone(final Properties properties) {
    if (properties.isEmpty()) return;

    synchronized (properties) {
      for (final String key : properties.stringPropertyNames()) {
        final String value = properties.getProperty(key);
        if (!Strings.isNullOrEmpty(value)) {
          data.put(key, value);
        }
      }
    }
  }
}
