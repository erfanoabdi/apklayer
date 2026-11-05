/* The PipeWire camera backend (libapklayer-camera-pipewire.so), its cameras: the desktop's PipeWire video sources (libcamera, V4L2 or any
 * Video/Source node) with the raw sizes they offer. Streams: pipewire.c. Option: role=camera (default: only nodes whose media.role is
 * Camera, as libcamera's and V4L2's are; role=any takes every Video/Source). */
#include "apklayer/camera.h"
#include "pipewire.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>

#include <pipewire/pipewire.h>
#include <spa/param/video/format-utils.h>
#include <spa/pod/iter.h>

static int any_role;

struct listing {
    struct pw_main_loop *loop;
    struct pw_core *core;
    struct pw_registry *registry;
    struct spa_hook core_listener, registry_listener;
    int pending;
    struct apklayer_camera_info cams[16];
    struct pw_proxy *nodes[16];
    struct spa_hook node_listeners[16];
    int count;
};

static void add_size(struct apklayer_camera_info *c, uint32_t w, uint32_t h)
{
    for (int i = 0; i < c->num_sizes; i++)
        if (c->sizes[i][0] == w && c->sizes[i][1] == h) return;
    if (c->num_sizes < APKLAYER_CAMERA_MAX_SIZES && w >= 16 && h >= 16 && !((w | h) & 1)) c->sizes[c->num_sizes][0] = w, c->sizes[c->num_sizes++][1] = h;
}

/* one EnumFormat of a node: a raw format the provider converts, and its sizes (fixed, a list, or a range: the common sizes inside it) */
static void on_param(void *data, int seq, uint32_t id, uint32_t index, uint32_t next, const struct spa_pod *param)
{
    (void)seq, (void)index, (void)next;
    struct apklayer_camera_info *c = data;
    uint32_t media_type, media_subtype;
    if (id != SPA_PARAM_EnumFormat || spa_format_parse(param, &media_type, &media_subtype) < 0 || media_type != SPA_MEDIA_TYPE_video ||
        media_subtype != SPA_MEDIA_SUBTYPE_raw)
        return;  // shortcut: MJPEG-only sizes are not offered (no decoder in the provider yet)
    const struct spa_pod_prop *format = spa_pod_find_prop(param, NULL, SPA_FORMAT_VIDEO_format);
    const struct spa_pod_prop *size = spa_pod_find_prop(param, NULL, SPA_FORMAT_VIDEO_size);
    if (!format || !size) return;
    uint32_t n, choice, usable = 0;
    const struct spa_pod *values = spa_pod_get_values(&format->value, &n, &choice);
    for (uint32_t i = 0; i < n && SPA_POD_TYPE(values) == SPA_TYPE_Id; i++) usable |= pw_camera_fourcc(((const uint32_t *)SPA_POD_BODY_CONST(values))[i]) != 0;
    if (!usable) return;
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

static const struct pw_node_events node_events = {PW_VERSION_NODE_EVENTS, .param = on_param};

static void on_global(void *data, uint32_t id, uint32_t permissions, const char *type, uint32_t version, const struct spa_dict *props)
{
    (void)permissions, (void)version;
    struct listing *l = data;
    if (strcmp(type, PW_TYPE_INTERFACE_Node) || !props || l->count >= 16) return;
    const char *klass = spa_dict_lookup(props, PW_KEY_MEDIA_CLASS), *role = spa_dict_lookup(props, PW_KEY_MEDIA_ROLE);
    if (!klass || strcmp(klass, "Video/Source") || (!any_role && (!role || strcmp(role, "Camera")))) return;
    const char *name = spa_dict_lookup(props, PW_KEY_NODE_NAME), *description = spa_dict_lookup(props, PW_KEY_NODE_DESCRIPTION);
    const char *location = spa_dict_lookup(props, "api.libcamera.location");
    struct apklayer_camera_info *c = &l->cams[l->count];
    memset(c, 0, sizeof *c);
    snprintf(c->id, sizeof c->id, "%s", name ? name : "");
    snprintf(c->name, sizeof c->name, "%s", description ? description : c->id);
    c->facing = location && !strcmp(location, "front") ? APKLAYER_CAM_FRONT : location && !strcmp(location, "back") ? APKLAYER_CAM_BACK : APKLAYER_CAM_EXTERNAL;
    c->orientation = c->facing == APKLAYER_CAM_FRONT ? 270 : 90;  /* upright on the landscape desktop: camera.h */
    c->max_fps = 30;
    /* its formats: asked now, answered before the next roundtrip ends */
    struct pw_node *node = pw_registry_bind(l->registry, id, type, PW_VERSION_NODE, 0);
    if (!node) return;
    l->nodes[l->count] = (struct pw_proxy *)node;
    pw_node_add_listener(node, &l->node_listeners[l->count], &node_events, c);
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
    struct apklayer_camera_info cams[1];
    return apklayer_camera_list(cams, 1) > 0 ? 10 : -1;  /* a real camera beats the fake; no camera: unusable here */
}

const char apklayer_camera_options[] = "role\tcamera\ttext\n";  /* camera or any (every video source) */

int apklayer_camera_init(int count, const char *const *options)
{
    for (int i = 0; i < count; i++) {
        if (!strcmp(options[i], "role=any")) any_role = 1;
        else if (!strcmp(options[i], "role=camera")) any_role = 0;
        else { fprintf(stderr, "camera pipewire: unknown option %s\n", options[i]); return -EINVAL; }
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
        for (int i = 0; i < l.count && result < max; i++)
            if (l.cams[i].num_sizes > 0) out[result++] = l.cams[i];
    }
    if (l.core) pw_core_disconnect(l.core);
    if (context) pw_context_destroy(context);
    if (l.loop) pw_main_loop_destroy(l.loop);
    return result;
}
