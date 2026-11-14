/* The PipeWire camera backend (libapklayer-camera-pipewire.so), its cameras: the desktop's PipeWire video sources (libcamera, V4L2 or any
 * Video/Source node) with the raw sizes they offer, and the sizes they offer only as MJPEG (most USB webcams' larger ones: the provider
 * decodes those). Streams: pipewire.c. Option: role=camera (default: only nodes whose media.role is
 * Camera, as libcamera's and V4L2's are; role=any takes every Video/Source). Option mount=[NAME:]DEGREES (repeatable): the camera is
 * turned with its display (a webcam on a portrait monitor): DEGREES (0, 90, 180, 270) are added to its sensor orientation; NAME: only for
 * the nodes whose name or description contains it. */
#include "apklayer/camera.h"
#include "apklayer/sound_server.h"
#include "pipewire.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <pipewire/pipewire.h>
#include <spa/param/video/format-utils.h>
#include <spa/pod/iter.h>

static int any_role;
#define MAX_MOUNTS 8
static struct { char name[64]; int degrees; } mounts[MAX_MOUNTS];  /* name "": every camera */
static int mount_count;

struct entry {
    struct apklayer_camera_info info;  /* its raw sizes */
    struct apklayer_camera_info mjpeg;  /* its MJPEG sizes (only num_sizes and sizes used) */
};

/* the last listing's MJPEG-only sizes per camera, for the streams */
#define MAX_NODES 64  /* camera nodes looked at: an Intel IPU6 laptop has ~30 raw-Bayer ones the provider cannot take */
static struct apklayer_camera_info mjpeg_only[MAX_NODES];
static int mjpeg_only_count;

int pw_camera_mjpeg_only(const char *id, uint32_t width, uint32_t height)
{
    for (int i = 0; i < mjpeg_only_count; i++)
        if (!strcmp(mjpeg_only[i].id, id))
            for (int s = 0; s < mjpeg_only[i].num_sizes; s++)
                if (mjpeg_only[i].sizes[s][0] == width && mjpeg_only[i].sizes[s][1] == height) return 1;
    return 0;
}

struct listing {
    struct pw_main_loop *loop;
    struct pw_core *core;
    struct pw_registry *registry;
    struct spa_hook core_listener, registry_listener;
    int pending;
    struct entry cams[MAX_NODES];
    struct pw_proxy *nodes[MAX_NODES];
    struct spa_hook node_listeners[MAX_NODES];
    int count;
};

static void add_size(struct apklayer_camera_info *c, uint32_t w, uint32_t h)
{
    for (int i = 0; i < c->num_sizes; i++)
        if (c->sizes[i][0] == w && c->sizes[i][1] == h) return;
    if (c->num_sizes < APKLAYER_CAMERA_MAX_SIZES && w >= 16 && h >= 16 && !((w | h) & 1)) c->sizes[c->num_sizes][0] = w, c->sizes[c->num_sizes++][1] = h;
}

/* one EnumFormat of a node: a raw format the provider converts or MJPEG, and its sizes (fixed, a list, or a range: the common sizes inside it) */
static void on_param(void *data, int seq, uint32_t id, uint32_t index, uint32_t next, const struct spa_pod *param)
{
    (void)seq, (void)index, (void)next;
    struct entry *e = data;
    struct apklayer_camera_info *c = &e->info;
    uint32_t media_type, media_subtype, n, choice;
    if (id != SPA_PARAM_EnumFormat || spa_format_parse(param, &media_type, &media_subtype) < 0 || media_type != SPA_MEDIA_TYPE_video) return;
    const struct spa_pod_prop *size = spa_pod_find_prop(param, NULL, SPA_FORMAT_VIDEO_size);
    if (!size) return;
    const struct spa_pod *values;
    if (media_subtype == SPA_MEDIA_SUBTYPE_mjpg) {
        c = &e->mjpeg;
    } else if (media_subtype == SPA_MEDIA_SUBTYPE_raw) {
        const struct spa_pod_prop *format = spa_pod_find_prop(param, NULL, SPA_FORMAT_VIDEO_format);
        if (!format) return;
        uint32_t usable = 0;
        values = spa_pod_get_values(&format->value, &n, &choice);
        for (uint32_t i = 0; i < n && SPA_POD_TYPE(values) == SPA_TYPE_Id; i++) usable |= pw_camera_fourcc(((const uint32_t *)SPA_POD_BODY_CONST(values))[i]) != 0;
        if (!usable) return;
    } else {
        return;
    }
    values = spa_pod_get_values(&size->value, &n, &choice);
    if (SPA_POD_TYPE(values) != SPA_TYPE_Rectangle) return;
    const struct spa_rectangle *r = SPA_POD_BODY_CONST(values);
    if (choice == SPA_CHOICE_None || choice == SPA_CHOICE_Enum) {
        for (uint32_t i = choice == SPA_CHOICE_Enum && n > 1 ? 1 : 0; i < n; i++) add_size(c, r[i].width, r[i].height);
    } else if ((choice == SPA_CHOICE_Range || choice == SPA_CHOICE_Step) && n >= 3) {
        static const uint32_t common[][2] = {{320, 240}, {640, 480}, {1280, 720}, {1920, 1080}};
        for (size_t i = 0; i < sizeof common / sizeof *common; i++)
            if (common[i][0] >= r[1].width && common[i][1] >= r[1].height && common[i][0] <= r[2].width && common[i][1] <= r[2].height) add_size(c, common[i][0], common[i][1]);
    }
}

static int by_area(const void *a, const void *b)
{
    const uint32_t *x = a, *y = b;
    uint64_t ax = (uint64_t)x[0] * x[1], ay = (uint64_t)y[0] * y[1];
    return ax < ay ? -1 : ax > ay;
}

static const struct pw_node_events node_events = {PW_VERSION_NODE_EVENTS, .param = on_param};

static void on_global(void *data, uint32_t id, uint32_t permissions, const char *type, uint32_t version, const struct spa_dict *props)
{
    (void)permissions, (void)version;
    struct listing *l = data;
    if (strcmp(type, PW_TYPE_INTERFACE_Node) || !props || l->count >= MAX_NODES) return;
    const char *klass = spa_dict_lookup(props, PW_KEY_MEDIA_CLASS), *role = spa_dict_lookup(props, PW_KEY_MEDIA_ROLE);
    if (!klass || strcmp(klass, "Video/Source") || (!any_role && (!role || strcmp(role, "Camera")))) return;
    const char *name = spa_dict_lookup(props, PW_KEY_NODE_NAME), *description = spa_dict_lookup(props, PW_KEY_NODE_DESCRIPTION);
    const char *location = spa_dict_lookup(props, "api.libcamera.location");
    struct entry *e = &l->cams[l->count];
    memset(e, 0, sizeof *e);
    struct apklayer_camera_info *c = &e->info;
    snprintf(c->id, sizeof c->id, "%s", name ? name : "");
    snprintf(c->name, sizeof c->name, "%s", description ? description : c->id);
    c->facing = location && !strcmp(location, "front") ? APKLAYER_CAM_FRONT : location && !strcmp(location, "back") ? APKLAYER_CAM_BACK : APKLAYER_CAM_EXTERNAL;
    c->orientation = c->facing == APKLAYER_CAM_FRONT ? 270 : 90;  /* upright on the landscape desktop: camera.h */
    for (int i = 0; i < mount_count; i++)
        if (!mounts[i].name[0] || (name && strstr(name, mounts[i].name)) || (description && strstr(description, mounts[i].name)))
            c->orientation = (c->orientation + mounts[i].degrees) % 360;
    c->max_fps = 30;
    /* its formats: asked now, answered before the next roundtrip ends */
    struct pw_node *node = pw_registry_bind(l->registry, id, type, PW_VERSION_NODE, 0);
    if (!node) return;
    l->nodes[l->count] = (struct pw_proxy *)node;
    pw_node_add_listener(node, &l->node_listeners[l->count], &node_events, e);
    pw_node_enum_params(node, 0, SPA_PARAM_EnumFormat, 0, UINT32_MAX, NULL);
    l->count++;
}

static const struct pw_registry_events registry_events = {PW_VERSION_REGISTRY_EVENTS, .global = on_global};

static void on_done(void *data, uint32_t id, int seq)
{
    struct listing *l = data;
    if (id == PW_ID_CORE && seq == l->pending) pw_main_loop_quit(l->loop);
}

static const struct pw_core_events core_events = {PW_VERSION_CORE_EVENTS, .done = on_done};

const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI;
int apklayer_camera_probe(void)
{
    if (!apklayer_pipewire_answers()) return -1;  /* no daemon: no client context (its config warnings on a host without PipeWire) */
    struct apklayer_camera_info cams[1];
    return apklayer_camera_list(cams, 1) > 0 ? 10 : -1;  /* a real camera beats the fake; no camera: unusable here */
}

const char apklayer_camera_needs[] = "a PipeWire daemon with a camera";
const char apklayer_camera_options[] = "role\tcamera\ttext\nmount\t\ttext\n";  /* role: camera or any (every video source); mount: [NAME:]DEGREES */

int apklayer_camera_init(int count, const char *const *options)
{
    for (int i = 0; i < count; i++) {
        if (!strcmp(options[i], "role=any")) any_role = 1;
        else if (!strcmp(options[i], "role=camera")) any_role = 0;
        else if (!strncmp(options[i], "mount=", 6)) {
            const char *v = options[i] + 6, *colon = strrchr(v, ':');
            char *end;
            long deg = strtol(colon ? colon + 1 : v, &end, 10);
            if (*end || deg < 0 || deg % 90 || deg > 270 || mount_count == MAX_MOUNTS || (colon && colon - v >= (long)sizeof mounts[0].name)) return -EINVAL;
            snprintf(mounts[mount_count].name, sizeof mounts[0].name, "%.*s", colon ? (int)(colon - v) : 0, v);
            mounts[mount_count++].degrees = deg;
        } else { fprintf(stderr, "camera pipewire: unknown option %s\n", options[i]); return -EINVAL; }
    }
    return 0;
}

int apklayer_camera_list(struct apklayer_camera_info *out, int max)
{
    pw_init(NULL, NULL);
    struct listing l = {0};
    l.loop = pw_main_loop_new(NULL);
    struct pw_context *context = l.loop ? pw_context_new(pw_main_loop_get_loop(l.loop), NULL, 0) : NULL;
    l.core = context ? pw_context_connect(context, NULL, 0) : NULL;
    int result = -ECONNREFUSED;
    if (l.core) {
        l.registry = pw_core_get_registry(l.core, PW_VERSION_REGISTRY, 0);
        pw_core_add_listener(l.core, &l.core_listener, &core_events, &l);
        pw_registry_add_listener(l.registry, &l.registry_listener, &registry_events, &l);
        for (int round = 0; round < 2; round++) {  /* every global announced, then every camera's formats */
            l.pending = pw_core_sync(l.core, PW_ID_CORE, 0);
            pw_main_loop_run(l.loop);
        }
        for (int i = 0; i < l.count; i++) spa_hook_remove(&l.node_listeners[i]), pw_proxy_destroy(l.nodes[i]);
        spa_hook_remove(&l.registry_listener);
        pw_proxy_destroy((struct pw_proxy *)l.registry);
        result = 0;
        mjpeg_only_count = 0;
        for (int i = 0; i < l.count && result < max; i++) {
            struct apklayer_camera_info *c = &l.cams[i].info, *m = &l.cams[i].mjpeg, *only = &mjpeg_only[mjpeg_only_count];
            memset(only, 0, sizeof *only);
            snprintf(only->id, sizeof only->id, "%s", c->id);
            for (int s = 0; s < m->num_sizes; s++) {
                int raw = 0;
                for (int r = 0; r < c->num_sizes; r++) raw |= c->sizes[r][0] == m->sizes[s][0] && c->sizes[r][1] == m->sizes[s][1];
                if (!raw && c->num_sizes < APKLAYER_CAMERA_MAX_SIZES) add_size(c, m->sizes[s][0], m->sizes[s][1]), add_size(only, m->sizes[s][0], m->sizes[s][1]);
            }
            qsort(c->sizes, c->num_sizes, sizeof c->sizes[0], by_area);  /* the provider takes the last as the largest */
            if (only->num_sizes) mjpeg_only_count++;
            if (c->num_sizes > 0) out[result++] = *c;
        }
    }
    if (l.core) pw_core_disconnect(l.core);
    if (context) pw_context_destroy(context);
    if (l.loop) pw_main_loop_destroy(l.loop);
    return result;
}
