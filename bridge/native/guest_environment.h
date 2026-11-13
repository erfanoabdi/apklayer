#ifndef APKLAYER_GUEST_ENVIRONMENT_H
#define APKLAYER_GUEST_ENVIRONMENT_H
/* Active ABI libc owns the strings and the environ pointer slot. Register before app native loads. */
struct libc_bio_environment_backend {
    char *(*get)(const char *);
    int (*set)(const char *, const char *, int);
    int (*unset)(const char *);
    int (*put)(char *);
    int (*clear)(void);
    char ***slot;
};
#endif
