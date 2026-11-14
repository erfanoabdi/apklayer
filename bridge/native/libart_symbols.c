/* libart.so's ART symbols (the library at /apex/com.android.art/lib64/libart.so): the ones hook frameworks, anti-tamper SDKs and ElfImg-style
 * resolvers look up in its .dynsym (docs/runtime-identity-matrix.md, "libart.so file"). This runtime is a JVM, not ART: the data is a zeroed
 * art::Runtime and the functions answer what is harmless (nothing, null, false) or stop the process with a message naming the function, so a
 * hook that would patch ART's code never silently corrupts the JVM. Names as art/ declares them (Android 13/14, 64-bit). */
#include <stdlib.h>
#include <unistd.h>

#if defined(__aarch64__)
#define NOTHING "ret\n"
#define ZERO "mov x0, #0\n ret\n"
#define ABORT(label) "adr x0, " label "\n b art_stub_called\n"
#define TYPE "%function"
#elif defined(__x86_64__)
#define NOTHING "ret\n"
#define ZERO "xor %eax, %eax\n ret\n"
#define ABORT(label) "lea " label "(%rip), %rdi\n jmp art_stub_called\n"
#define TYPE "@function"
#else
#error "no stub for this architecture"
#endif

#define STUB(sym, body) __asm__(".text\n.p2align 2\n.globl " sym "\n.type " sym "," TYPE "\n" sym ":\n" body ".size " sym ", .-" sym "\n");
#define A(sym) STUB(sym, ABORT("1f") "1: .asciz \"" sym "\"\n")
#define N(sym) STUB(sym, NOTHING)
#define Z(sym) STUB(sym, ZERO)

__attribute__((used, visibility("hidden"))) void art_stub_called(const char *name)
{
    static const char head[] = "libart.so: ART function called in a JVM runtime: ";
    (void)!write(2, head, sizeof head - 1);
    size_t n = 0;
    while (name[n]) n++;
    (void)!write(2, name, n);
    (void)!write(2, "\n", 1);
    abort();
}

/* art::Runtime::instance_ (Runtime*): a block of zeros, so a reader finds an empty runtime and not a wild pointer */
static char runtime_block[4096] __attribute__((aligned(16)));
void *_ZN3art7Runtime9instance_E = runtime_block;

Z("_ZN3art6Thread14CurrentFromGdbEv")
Z("_ZNK3art6Thread13DecodeJObjectEP8_jobject")
Z("_ZN3art3jit3Jit11CompileMethodEPNS_9ArtMethodEPNS_6ThreadENS_15CompilationKindEb")
Z("_ZN3art2gc4Heap12CollectGarbageEbNS0_7GcCauseE")
N("_ZN3art7Runtime17SetJavaDebuggableEb")
N("_ZN3art7Runtime19DeoptimizeBootImageEv")
N("_ZN3art11ClassLinker22FixupStaticTrampolinesEPNS_6ThreadENS_6ObjPtrINS_6mirror5ClassEEE")
N("_ZN3art11ClassLinker22MakeInitializedClassesVisiblyInitializedEPNS_6ThreadEb")
N("_ZN3art10ThreadList10SuspendAllEPKcb")
N("_ZN3art10ThreadList9ResumeAllEv")
A("_ZN3art9ArtMethod6InvokeEPNS_6ThreadEPjjPNS_6JValueEPKc")
A("_ZN3art9ArtMethod12PrettyMethodEb")
A("_ZN3art11ClassLinker12VisitClassesEPNS_12ClassVisitorE")
A("art_quick_to_interpreter_bridge")
A("art_quick_generic_jni_trampoline")
A("art_quick_resolution_trampoline")
A("art_quick_imt_conflict_trampoline")
A("art_quick_proxy_invoke_handler")
A("art_quick_deoptimize")
A("art_quick_osr_stub")
