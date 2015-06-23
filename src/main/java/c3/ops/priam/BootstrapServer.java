package c3.ops.priam;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BootstrapServer {
  private static final Logger logger = LoggerFactory.getLogger(BootstrapServer.class.getName());

  public static void main(String[] args) throws Exception {
    try {
      Server server = new Server(Integer.valueOf(System.getProperty("opsagent.opsagentPORT")));
      String webDir = BootstrapServer.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
      WebAppContext webAppContext = new WebAppContext(webDir, "/");
      webAppContext.setDescriptor(webAppContext + "/WEB-INF/web.xml");
      server.setHandler(webAppContext);
      server.start();
      server.join();
    } catch (Exception e) {
      logger.error("Couldn't start Priam Server \n" + e.getMessage());
    }
  }
}
