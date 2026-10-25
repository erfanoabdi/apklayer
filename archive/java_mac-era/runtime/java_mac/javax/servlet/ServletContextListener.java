package javax.servlet;
import java.util.EventListener;
public interface ServletContextListener extends EventListener {
    default void contextInitialized(ServletContextEvent sce) {}
    default void contextDestroyed(ServletContextEvent sce) {}
}
