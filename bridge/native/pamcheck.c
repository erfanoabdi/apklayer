// apklayer-pamcheck USER [SERVICE]: whether the password on stdin (NUL- or EOF-terminated) passes the PAM stack of SERVICE (default "login")
// for USER, as a screen locker asks: any PAM modules (pam_unix, sssd, LDAP, FreeIPA, yubikey) answer, no privilege needed (pam_unix runs
// unix_chkpwd itself). libpam is loaded at run time (its header is not a build dependency). Exit 0 accepted, 1 refused, 2 libpam unusable.
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

typedef struct { int style; const char *msg; } pam_msg;
typedef struct { char *resp; int retcode; } pam_resp;
typedef struct { int (*conv)(int, const pam_msg **, pam_resp **, void *); void *data; } pam_conv_t;
enum { PAM_SUCCESS = 0, PAM_BUF_ERR = 5, PAM_CONV_ERR = 19, PAM_PROMPT_ECHO_OFF = 1, PAM_PROMPT_ECHO_ON = 2 };

// every prompt for a secret or an echoed answer gets the password; messages and errors need no answer
static int converse(int count, const pam_msg **messages, pam_resp **responses, void *password) {
    pam_resp *out = calloc(count, sizeof *out);
    if (!out) return PAM_BUF_ERR;
    for (int i = 0; i < count; i++) {
        if (messages[i]->style != PAM_PROMPT_ECHO_OFF && messages[i]->style != PAM_PROMPT_ECHO_ON) continue;
        if (!(out[i].resp = strdup(password))) { for (int j = 0; j < i; j++) free(out[j].resp); free(out); return PAM_CONV_ERR; }
    }
    *responses = out;
    return PAM_SUCCESS;
}

int main(int argc, char **argv) {
    if (argc < 2) return 2;
    void *pam = dlopen("libpam.so.0", RTLD_NOW);
    int (*start)(const char *, const char *, const pam_conv_t *, void **) = pam ? dlsym(pam, "pam_start") : NULL;
    int (*authenticate)(void *, int) = pam ? dlsym(pam, "pam_authenticate") : NULL;
    int (*end)(void *, int) = pam ? dlsym(pam, "pam_end") : NULL;
    if (!start || !authenticate || !end) return 2;
    char password[1024];
    ssize_t n = read(0, password, sizeof password - 1);
    password[n < 0 ? 0 : n] = 0;  // the first NUL ends it
    if (!password[0]) return 1;
    pam_conv_t conv = {converse, password};
    void *handle = NULL;
    int status = start(argc > 2 ? argv[2] : "login", argv[1], &conv, &handle);
    if (status == PAM_SUCCESS) { status = authenticate(handle, 0); end(handle, status); }
    memset(password, 0, sizeof password);
    return status == PAM_SUCCESS ? 0 : 1;
}
