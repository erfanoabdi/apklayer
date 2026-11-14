/* libart.so's JNI part (the library at /apex/com.android.art/lib64/libart.so, bridge/native/meson.build): the JNIEnv and JavaVM function tables
 * the JVM provides are routed through jumps in this library, so a guest's dladdr of (*env)->FindClass names libart.so as on Android
 * (docs/runtime-identity-matrix.md, JNI rows). Each entry is a thunk named as ART names the function (art::JNI<false>::FindClass ...), a local symbol
 * of .symtab like the build Android ships (its dladdr answers no symbol name for them), that jumps to the function the table held before. The
 * names are art_jni_names.inc (tools/gen/art-jni.py). The JNI invocation API (JNI_GetCreatedJavaVMs ...) is ART's answers, as jni_guest_surface.c's. */
#include <stdlib.h>

#define JNI_ENTRIES 233  /* reserved0..3 and the 229 functions up to GetObjectRefType: ART 13's table (a newer JDK's has more) */
#define VM_ENTRIES 8

static void *art_jni_slots[JNI_ENTRIES], *art_vm_slots[VM_ENTRIES];
static void *art_vm;
/* The tables a guest's `*env` and `*vm` point to: data of this library, as ART's (dladdr of *env names gJniNativeInterface, libart.so; the invoke
 * table is a local const in ART, no symbol). jni_art.c points the JNIEnvs and the JavaVM here. */
#define JNI_TABLE_MAX 256
__attribute__((visibility("default"))) void *_ZN3art27JniNativeInterfaceFunctionsILb0EE19gJniNativeInterfaceE[JNI_TABLE_MAX];
static void *art_invoke_table[VM_ENTRIES];
static void *const art_slots_used[] __attribute__((used)) = { art_jni_slots, art_vm_slots };  /* the asm names the arrays: keep them */

#if defined(__aarch64__)
#define THUNK(arr, i) "adrp x16, " arr "+8*" #i "\n ldr x16, [x16, :lo12:" arr "+8*" #i "]\n br x16\n"
#define TYPE "%function"
#define VA "St9__va_list"
#elif defined(__x86_64__)
#define THUNK(arr, i) "jmp *" arr "+8*" #i "(%rip)\n"
#define TYPE "@function"
#define VA "P13__va_list_tag"
#else
#error "no thunk for this architecture"
#endif
#define THUNK_ASM(arr, i, sym) __asm__(".text\n.p2align 2\n.local " sym "\n.type " sym "," TYPE "\n" sym ":\n" THUNK(arr, i) ".size " sym ", .-" sym "\n");

#define X(i, sym) THUNK_ASM("art_jni_slots", i, sym)
#define Y(i, sym) THUNK_ASM("art_vm_slots", i, sym)
#include "art_jni_names.inc"
#undef X
#undef Y

#define X(i, sym) extern void art_thunk_##i(void) __asm__(sym);
#define Y(i, sym) extern void art_vm_thunk_##i(void) __asm__(sym);
#include "art_jni_names.inc"
#undef X
#undef Y
#define X(i, sym) [i] = art_thunk_##i,
#define Y(i, sym)
static void *const jni_thunks[JNI_ENTRIES] = {
#include "art_jni_names.inc"
};
#undef X
#undef Y
#define X(i, sym)
#define Y(i, sym) [i] = art_vm_thunk_##i,
static void *const vm_thunks[VM_ENTRIES] = {
#include "art_jni_names.inc"
};

/* The runtime hands over its tables through the environment (the address of this struct, hex) when it loads the library: no exported symbol of
 * ours for a scanner to find. Each table entry is replaced by its thunk. */
struct art_tables { void *vm; void **jni; void **invoke; unsigned long jni_entries; void *jni_copy, *invoke_copy; };

__attribute__((constructor)) static void bind(void)
{
    const char *address = getenv("APKLAYER_ART_TABLES");
    if (!address) return;
    unsigned long value = 0;  /* not strtoul: glibc's C23 name of it (__isoc23_strtoul) is not in bionic */
    for (const char *c = address; (*c >= '0' && *c <= '9') || (*c >= 'a' && *c <= 'f'); c++) value = value * 16 + (*c <= '9' ? *c - '0' : *c - 'a' + 10);
    struct art_tables *t = (void *)value;
    unsetenv("APKLAYER_ART_TABLES");
    art_vm = t->vm;
    for (int i = 4; i < JNI_ENTRIES; i++) if (t->jni[i] && t->jni[i] != jni_thunks[i]) { art_jni_slots[i] = t->jni[i]; t->jni[i] = jni_thunks[i]; }
    for (int i = 3; i < VM_ENTRIES; i++) if (t->invoke[i] && t->invoke[i] != vm_thunks[i]) { art_vm_slots[i] = t->invoke[i]; t->invoke[i] = vm_thunks[i]; }
    unsigned long n = t->jni_entries < JNI_TABLE_MAX ? t->jni_entries : JNI_TABLE_MAX;
    for (unsigned long i = 0; i < n; i++) _ZN3art27JniNativeInterfaceFunctionsILb0EE19gJniNativeInterfaceE[i] = t->jni[i];
    for (int i = 0; i < VM_ENTRIES; i++) art_invoke_table[i] = t->invoke[i];
    t->jni_copy = _ZN3art27JniNativeInterfaceFunctionsILb0EE19gJniNativeInterfaceE;
    t->invoke_copy = art_invoke_table;
}

/* art/runtime/jni/java_vm_ext.cc: the invocation API ART exports */
int JNI_GetCreatedJavaVMs(void **vms, int size, int *count)
{
    if (size <= 0) *count = 0;
    else { *count = 1; vms[0] = art_vm; }
    return 0;
}

int JNI_GetDefaultJavaVMInitArgs(void *args) { (void)args; return -1; }  /* JNI_ERR: unsupported */
int JNI_CreateJavaVM(void **vm, void **env, void *args) { (void)vm; (void)env; (void)args; return -1; }  /* JNI_ERR: a runtime exists already */
