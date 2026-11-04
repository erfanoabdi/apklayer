/* Raising a window (MSG_ACTIVATE): `apklayer run` of an app that already runs brings its window forward, as Android brings the task forward.
 * xdg-activation-v1 with the launcher's own token (XDG_ACTIVATION_TOKEN, which a desktop launcher gives the command it starts), or without one
 * a token this shell asks for (the compositor's focus-stealing rules decide: it may only mark the window as wanting attention). */
#include "shell.h"
#include "xdg-activation-v1-client-protocol.h"

struct xdg_activation_v1 *activation;

static void token_done(void *d, struct xdg_activation_token_v1 *t, const char *token)
{
    struct window *w = &windows[(intptr_t)d];
    if (w->surface) xdg_activation_v1_activate(activation, token, w->surface);  /* the window may have closed meanwhile */
    xdg_activation_token_v1_destroy(t);
}
static const struct xdg_activation_token_v1_listener token_listener = { token_done };

/* {utf8 window title "\0" utf8 token (empty: none)} */
void activate_message(const uint8_t *p, uint32_t len)
{
    char text[512];
    if (len == 0 || len >= sizeof text) return;
    memcpy(text, p, len);
    text[len] = 0;
    size_t title_len = strlen(text);
    const char *token = title_len + 1 < len ? text + title_len + 1 : "";
    struct window *w = NULL;
    for (int i = 0; i < MAX_WINDOWS && !w; i++)
        if (windows[i].client && windows[i].surface && !strcmp(windows[i].title, text)) w = &windows[i];
    fprintf(stderr, "[shell] activate \"%s\": %s\n", text, !w ? "no such window" : !activation ? "no xdg-activation" : token[0] ? "launcher token" : "own token");
    if (!w || !activation) return;
    if (token[0]) {
        xdg_activation_v1_activate(activation, token, w->surface);
        return;
    }
    struct xdg_activation_token_v1 *t = xdg_activation_v1_get_activation_token(activation);
    xdg_activation_token_v1_add_listener(t, &token_listener, (void *)(intptr_t)(w - windows));
    /* the requester is the window that has the keyboard (another app of this shell, if any), with its last input: what a compositor grants */
    if (seat && last_input_serial) xdg_activation_token_v1_set_serial(t, last_input_serial, seat);
    xdg_activation_token_v1_set_surface(t, keyboard_focus && keyboard_focus->surface ? keyboard_focus->surface : w->surface);
    xdg_activation_token_v1_commit(t);
}
