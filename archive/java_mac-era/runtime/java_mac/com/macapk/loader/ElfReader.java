// ElfReader -- minimal ELF64 reader for AArch64 .so files.
//
// Reads the DT_NEEDED entries from an ELF's .dynamic section so the
// AppClassLoader can pre-load a library's dependencies via
// System.loadLibrary before the dependent library's own
// __mod_init_func runs and tries to resolve symbols from them.
//
// The Android linker auto-walks DT_NEEDED at dlopen time on a real
// device; on macOS, dlopen of our translated wrapper doesn't (the
// wrapper has @rpath dependencies that don't match the Android
// soname). So we mirror the Android linker's pre-resolve pass at
// the AppClassLoader layer.
//
// Scope: ELF64 little-endian only (AArch64). PT_DYNAMIC parsing
// only; we don't follow R_AARCH64_* relocations or load PT_LOADs
// (the runtime ELF loader handles that).
//
// Reference: System V Application Binary Interface, AArch64
// Processor Supplement
// (https://github.com/ARM-software/abi-aa).
package com.macapk.loader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ElfReader {
    private ElfReader() {}  // static-only

    // ELF / dynamic-tag constants we care about.
    private static final int PT_LOAD    = 1;
    private static final int PT_DYNAMIC = 2;
    private static final long DT_NULL    = 0;
    private static final long DT_NEEDED  = 1;
    private static final long DT_STRTAB  = 5;
    private static final long DT_STRSZ   = 10;

    /**
     * Return the largest (p_vaddr + p_memsz) over all PT_LOAD segments of
     * an ELF32 (ARM) .so -- i.e. the image's link-time vaddr footprint.
     * Used by AppClassLoader to assign each ARM32 .so a non-overlapping
     * per-image load bias in the shared guest arena (multi-.so collision
     * fix). Returns 0 on parse failure or non-ELF32 input (caller then
     * treats the image as bias 0 / its own slot). ELF32 LE header: e_phoff
     * is a uint32 at file offset 28; e_phentsize/e_phnum are uint16 at
     * 42/44; each program header is { p_type, p_offset, p_vaddr, p_paddr,
     * p_filesz, p_memsz, p_flags, p_align } (8 x uint32).
     */
    public static long readMaxVaddr(java.io.File soFile) {
        try (RandomAccessFile raf = new RandomAccessFile(soFile, "r")) {
            byte[] ident = new byte[16];
            raf.readFully(ident);
            if (ident[0] != 0x7f || ident[1] != 'E'
                || ident[2] != 'L' || ident[3] != 'F') {
                return 0;
            }
            if (ident[4] != 1 || ident[5] != 1) {
                return 0;  // not ELF32 LE
            }
            // e_phoff (uint32 @28), e_phentsize (uint16 @42), e_phnum (@44).
            byte[] tail = new byte[36];   // file bytes 16..52
            raf.readFully(tail);
            ByteBuffer ehdr = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
            ehdr.position(28 - 16);
            long e_phoff = ehdr.getInt() & 0xffffffffL;
            ehdr.position(42 - 16);
            int e_phentsize = ehdr.getShort() & 0xffff;
            int e_phnum     = ehdr.getShort() & 0xffff;
            if (e_phentsize < 32 || e_phnum <= 0 || e_phnum > 256) return 0;
            raf.seek(e_phoff);
            byte[] phs = new byte[e_phentsize * e_phnum];
            raf.readFully(phs);
            long maxEnd = 0;
            for (int i = 0; i < e_phnum; ++i) {
                ByteBuffer p = ByteBuffer.wrap(phs, i * e_phentsize, e_phentsize)
                                          .order(ByteOrder.LITTLE_ENDIAN);
                int p_type = p.getInt();
                if (p_type != PT_LOAD) continue;
                p.position(p.position() + 4);   // p_offset
                long p_vaddr = p.getInt() & 0xffffffffL;
                p.position(p.position() + 4);   // p_paddr
                p.position(p.position() + 4);   // p_filesz
                long p_memsz = p.getInt() & 0xffffffffL;
                long end = p_vaddr + p_memsz;
                if (end > maxEnd) maxEnd = end;
            }
            return maxEnd;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Return the soname strings from each DT_NEEDED entry in the ELF,
     * in their original order. Empty list on parse failure or if no
     * DT_NEEDED entries exist.
     */
    public static List<String> readDtNeeded(java.io.File soFile) {
        try (RandomAccessFile raf = new RandomAccessFile(soFile, "r")) {
            byte[] ident = new byte[16];
            raf.readFully(ident);
            // ELF magic + class + endianness checks. We support exactly
            // ELF64 little-endian; anything else returns empty rather
            // than throwing, since the caller treats no-dependencies
            // and parse-failure identically (load the lib standalone).
            if (ident[0] != 0x7f || ident[1] != 'E'
                || ident[2] != 'L' || ident[3] != 'F') {
                return Collections.emptyList();
            }
            if (ident[4] != 2 || ident[5] != 1) {
                // not ELF64 LE
                return Collections.emptyList();
            }

            // ELF64 header layout starting at offset 16 (after e_ident):
            //   uint16 e_type, uint16 e_machine, uint32 e_version,
            //   uint64 e_entry, uint64 e_phoff, uint64 e_shoff,
            //   uint32 e_flags, uint16 e_ehsize,
            //   uint16 e_phentsize, uint16 e_phnum, ...
            byte[] tail = new byte[48];
            raf.readFully(tail);
            ByteBuffer ehdr = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
            // tail covers file bytes 16..64 (after e_ident). Layout:
            //   tail[0..16]:  e_type/e_machine/e_version/e_entry
            //   tail[16..24]: e_phoff (uint64)
            //   tail[24..32]: e_shoff
            //   tail[32..36]: e_flags
            //   tail[36..38]: e_ehsize
            //   tail[38..40]: e_phentsize
            //   tail[40..42]: e_phnum
            ehdr.position(16);
            long e_phoff = ehdr.getLong();
            ehdr.position(38);
            int e_phentsize = ehdr.getShort() & 0xffff;
            int e_phnum     = ehdr.getShort() & 0xffff;

            // Walk program headers; find PT_DYNAMIC.
            raf.seek(e_phoff);
            byte[] phs = new byte[e_phentsize * e_phnum];
            raf.readFully(phs);
            long dynOff = -1;
            long dynSize = -1;
            for (int i = 0; i < e_phnum; ++i) {
                ByteBuffer p = ByteBuffer.wrap(phs, i * e_phentsize, e_phentsize)
                                          .order(ByteOrder.LITTLE_ENDIAN);
                int p_type = p.getInt();
                if (p_type != PT_DYNAMIC) continue;
                p.position(p.position() + 4);  // p_flags
                long p_offset = p.getLong();
                p.position(p.position() + 8);  // p_vaddr
                p.position(p.position() + 8);  // p_paddr
                long p_filesz = p.getLong();
                dynOff  = p_offset;
                dynSize = p_filesz;
                break;
            }
            if (dynOff < 0) return Collections.emptyList();

            // PT_DYNAMIC contains a sequence of (int64 d_tag, int64 d_val)
            // pairs. We make two passes: first find DT_STRTAB / DT_STRSZ
            // (string table offset/size in image vaddr space, which for
            // ELF .so files is also the file offset for the .dynstr
            // section -- they're co-located). Then collect DT_NEEDED's
            // d_val entries (offsets into the string table).
            raf.seek(dynOff);
            byte[] dyn = new byte[(int) dynSize];
            raf.readFully(dyn);
            ByteBuffer db = ByteBuffer.wrap(dyn).order(ByteOrder.LITTLE_ENDIAN);

            long strtabVaddr = -1;
            long strtabSize  = -1;
            List<Long> neededOffsets = new ArrayList<>();
            for (int i = 0; i + 16 <= dyn.length; i += 16) {
                db.position(i);
                long tag = db.getLong();
                long val = db.getLong();
                if (tag == DT_NULL) break;
                if (tag == DT_STRTAB) strtabVaddr = val;
                else if (tag == DT_STRSZ) strtabSize = val;
                else if (tag == DT_NEEDED) neededOffsets.add(val);
            }
            if (strtabVaddr < 0 || strtabSize <= 0
                    || neededOffsets.isEmpty()) {
                return Collections.emptyList();
            }

            // The DT_STRTAB value is a virtual address. For ELF .so
            // files generated by the standard toolchain, the .dynstr
            // section's p_offset equals its p_vaddr in the containing
            // PT_LOAD -- so reading from file offset strtabVaddr lands
            // on the string table bytes. This holds for every Android
            // NDK-built library we've seen.
            raf.seek(strtabVaddr);
            byte[] strtab = new byte[(int) strtabSize];
            raf.readFully(strtab);

            List<String> out = new ArrayList<>(neededOffsets.size());
            for (long off : neededOffsets) {
                if (off < 0 || off >= strtab.length) continue;
                int end = (int) off;
                while (end < strtab.length && strtab[end] != 0) ++end;
                out.add(new String(strtab, (int) off, end - (int) off,
                                    java.nio.charset.StandardCharsets.UTF_8));
            }
            return out;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
    private static final int SHT_DYNSYM = 11;

    /**
     * Count the DEFINED dynamic symbols (st_shndx != SHN_UNDEF) of an ELF64 LE
     * .so whose name starts with {@code prefix} ("Java_" = JNI exports,
     * "JNI_OnLoad" = the load hook). Walks SHT_DYNSYM through the section
     * headers. Returns -1 when the section headers are absent or unreadable
     * (a stripped e_shnum == 0 image) so the caller can say "unknown" rather
     * than "none". Layouts, from bionic libc/kernel/uapi/linux/elf.h:
     * Elf64_Sym { u32 st_name; u8 st_info; u8 st_other; u16 st_shndx;
     * u64 st_value; u64 st_size; } = 24 bytes; Elf64_Shdr { u32 sh_name;
     * u32 sh_type; u64 sh_flags; u64 sh_addr; u64 sh_offset; u64 sh_size;
     * u32 sh_link; u32 sh_info; u64 sh_addralign; u64 sh_entsize; } = 64;
     * e_shoff @40, e_shentsize @58, e_shnum @60 of the ELF64 header.
     */
    public static int countDefinedDynsyms(java.io.File soFile, String prefix) {
        try (RandomAccessFile raf = new RandomAccessFile(soFile, "r")) {
            byte[] eh = new byte[64];
            raf.readFully(eh);
            if (eh[0] != 0x7f || eh[1] != 'E' || eh[2] != 'L' || eh[3] != 'F'
                    || eh[4] != 2 || eh[5] != 1) return -1;
            ByteBuffer e = ByteBuffer.wrap(eh).order(ByteOrder.LITTLE_ENDIAN);
            long shoff = e.getLong(40);
            int shentsize = e.getShort(58) & 0xffff;
            int shnum = e.getShort(60) & 0xffff;
            if (shoff <= 0 || shnum == 0 || shentsize < 64) return -1;
            if ((long) shentsize * shnum > (16L << 20)) return -1;
            byte[] sh = new byte[shentsize * shnum];
            raf.seek(shoff);
            raf.readFully(sh);
            ByteBuffer sb = ByteBuffer.wrap(sh).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < shnum; ++i) {
                int b = i * shentsize;
                if (sb.getInt(b + 4) != SHT_DYNSYM) continue;
                long symOff = sb.getLong(b + 24), symSize = sb.getLong(b + 32);
                int link = sb.getInt(b + 40);
                if (link <= 0 || link >= shnum) return -1;
                int lb = link * shentsize;
                long strOff = sb.getLong(lb + 24), strSize = sb.getLong(lb + 32);
                if (symSize <= 0 || symSize > (64L << 20) || strSize <= 0 || strSize > (64L << 20)) return -1;
                byte[] sym = new byte[(int) symSize];
                raf.seek(symOff);
                raf.readFully(sym);
                byte[] str = new byte[(int) strSize];
                raf.seek(strOff);
                raf.readFully(str);
                byte[] pfx = prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ByteBuffer yb = ByteBuffer.wrap(sym).order(ByteOrder.LITTLE_ENDIAN);
                int n = 0;
                for (int o = 0; o + 24 <= sym.length; o += 24) {
                    int shndx = yb.getShort(o + 6) & 0xffff;
                    if (shndx == 0) continue;            // SHN_UNDEF: imported, not defined here
                    int nm = yb.getInt(o);
                    if (nm < 0 || nm + pfx.length > str.length) continue;
                    boolean hit = true;
                    for (int k = 0; k < pfx.length && hit; ++k) hit = str[nm + k] == pfx[k];
                    if (hit) n++;
                }
                return n;
            }
            return -1;
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }
}
