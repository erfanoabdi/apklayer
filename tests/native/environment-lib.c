#include <stdlib.h>
extern char **environ, **__environ;
char ***environment_slot(void) { return &environ; }
char ***environment_alias(void) { return &__environ; }
char *environment_get(const char *name) { return getenv(name); }
int environment_set(const char *name, const char *value) { return setenv(name, value, 1); }
