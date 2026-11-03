#!/bin/bash
# Compile a subset of libcore's java.* tests (util, io, nio, lang; not net: network) against the tree's prebuilt JDK 21
# (read-only tree, output in /tmp/lctest) and run them with JUnit 4: a failure is a libcore-vs-OpenJDK behaviour difference (or a test of a libcore internal).
# Run in the tree's VM: orb -m ubuntu22-x86 /bin/bash tools/debug/libcore-tests-hotspot.sh  (failures: /tmp/lctest/run.txt). Sources that do not compile on the JDK are dropped.
set -e
cd ~/los24
J=prebuilts/jdk/jdk21/linux-x86/bin
W=/tmp/lctest
rm -rf $W; mkdir -p $W/c
CP=out/soong/.intermediates/external/junit/junit/linux_glibc_common/javac/junit.jar:out/soong/.intermediates/external/hamcrest/hamcrest-core/hamcrest/linux_glibc_common/javac/hamcrest.jar:out/soong/.intermediates/libcore/core-all/android_common/javac/core-all.jar
find libcore/luni/src/test/java/libcore/java/util libcore/luni/src/test/java/libcore/java/io libcore/luni/src/test/java/libcore/java/nio libcore/luni/src/test/java/libcore/java/lang -maxdepth 1 -name "*.java" > $W/files
wc -l < $W/files
for round in 1 2 3 4 5 6 7 8 9 10; do
    if $J/javac -nowarn -Xmaxerrs 100000 -proc:none -implicit:class -d $W/c -cp $CP -sourcepath libcore/support/src/test/java:libcore/test-rules/src/main/java:libcore/junit/src/main/java @$W/files > $W/err 2>&1; then echo "compiled in round $round"; break; fi
    grep -o "^libcore/luni/src/test/java/libcore/java/[^:]*\.java" $W/err | sort -u > $W/bad || true
    echo "round $round: $(wc -l < $W/bad) files with errors"
    [ -s $W/bad ] || { head -5 $W/err; exit 1; }
    grep -v -x -F -f $W/bad $W/files > $W/f2 || true; mv $W/f2 $W/files
done
echo "$(wc -l < $W/files) test sources compile"
sed -e 's#^libcore/luni/src/test/java/##' -e 's#\.java$##' -e 's#/#.#g' $W/files | grep 'Test$' | grep -v -e OldStackTraceElementTest > $W/classes  # needs libcore's own JNI test library
set +e
$J/java -Xmx2g -cp $W/c:$CP org.junit.runner.JUnitCore $(cat $W/classes) > $W/run.txt 2>&1
grep -E "^(OK|Tests run)" $W/run.txt
