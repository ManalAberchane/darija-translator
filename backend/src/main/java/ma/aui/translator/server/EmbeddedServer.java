package ma.aui.translator.server;

import jakarta.security.auth.message.config.AuthConfigFactory;
import ma.aui.translator.rest.TranslatorApplication;
import ma.aui.translator.security.BasicAuthModule;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.startup.Tomcat;
import org.glassfish.jersey.servlet.ServletContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * Embedded Apache Tomcat 11 hosting the Jersey (Jakarta REST) application under {@code /api}.
 * The Jakarta Authentication module is registered for the web application at startup.
 */
public class EmbeddedServer {

    private final Tomcat tomcat = new Tomcat();

    public EmbeddedServer(int port, TranslatorApplication application, BasicAuthModule authModule) {
        try {
            tomcat.setBaseDir(Files.createTempDirectory("darija-tomcat").toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        tomcat.setPort(port);
        tomcat.getConnector().setProperty("URIEncoding", "UTF-8");

        Context context = tomcat.addContext("", null);
        context.setRealm(new NullRealm());
        context.setRequestCharacterEncoding("UTF-8");
        context.setResponseCharacterEncoding("UTF-8");
        context.addServletContainerInitializer((classes, servletContext) ->
                AuthConfigFactory.getFactory().registerServerAuthModule(authModule, servletContext), null);

        Tomcat.addServlet(context, "jersey", new ServletContainer(application));
        context.addServletMappingDecoded("/api/*", "jersey");
    }

    public int start() throws LifecycleException {
        tomcat.start();
        return tomcat.getConnector().getLocalPort();
    }

    public void await() {
        tomcat.getServer().await();
    }

    public void stop() throws LifecycleException {
        tomcat.stop();
        tomcat.destroy();
    }
}
