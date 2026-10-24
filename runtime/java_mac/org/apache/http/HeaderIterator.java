package org.apache.http;
import java.util.Iterator;
public interface HeaderIterator extends Iterator<Object> {
    Header nextHeader();
    boolean hasNext();
    Object next();
}
