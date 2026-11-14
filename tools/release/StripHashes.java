// java StripHashes.java <module-info.class>: drops the ModuleHashes attribute (JDK 24+; jdk-image.sh builds an image from the jimage's modules without jmods)
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.nio.file.*;

void main(String[] a) throws Exception {
    var p = Path.of(a[0]); var cf = ClassFile.of();
    Files.write(p, cf.transformClass(cf.parse(p), ClassTransform.dropping(e -> e instanceof ModuleHashesAttribute)));
}
