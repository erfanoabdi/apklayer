/* plt-hook's library, loaded by bionic_translation's linker: one import, called through its PLT */
int plt_hook_target(void);
int plt_hook_call(void) { return plt_hook_target(); }
