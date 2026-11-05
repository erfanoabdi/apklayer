/* The D-Bus GNSS backends' shared part (session.c): the backend's thread, the latest fix, apklayer_gnss_next_fix, and a session that runs only
 * while the front asks for fixes. A session is a private connection to the bus: ending it (closing the connection) ends the location service's
 * client too (GeoClue 2 and 1, lomiri-location-service drop a client whose connection goes), so a backend only starts one. */
#ifndef APKLAYER_GNSS_SESSION_H
#define APKLAYER_GNSS_SESSION_H

#include "apklayer/gnss.h"
#include <gio/gio.h>

/* the bus the backend's service is on and the backend's name for its messages; once, from apklayer_gnss_init */
int gnss_session_init(GBusType bus, const char *name);
/* the latest fix, from any thread */
void gnss_session_report(const struct apklayer_gnss_fix *fix);
/* whether `name` answers on `bus` (Peer.Ping at `path`, which activates an activatable service): the backends' probe() */
int gnss_session_ping(GBusType bus, const char *name, const char *path);

/* by the backend, on the backend's thread (its context is the thread default): start the location service's client on `bus`; 0, or -1 when
 * it refused (the message is printed: no fixes this session) */
int gnss_source_start(GDBusConnection *bus);

#endif
