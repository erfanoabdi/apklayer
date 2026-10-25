package org.apache.http.conn.params;
import org.apache.http.conn.routing.HttpRoute;
public interface ConnPerRoute {
    int getMaxForRoute(HttpRoute route);
}
