package org.apache.http.entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** A streamed, non-repeatable entity that reads its content from an
 *  {@link InputStream}.
 *
 *  Part of the {@code org.apache.http.legacy} optional shared library, which a
 *  device provides to any app whose manifest declares
 *  {@code <uses-library android:name="org.apache.http.legacy"/>}. Mirrors
 *  Apache HttpClient 4.x's InputStreamEntity, which is what AOSP's fork ships.
 *
 *  The ContentType-taking constructor is deliberately absent: it arrived in
 *  HttpClient 4.2 and there is no {@code entity.ContentType} in the legacy
 *  surface, so adding it here would advertise a type we do not have.
 */
public class InputStreamEntity extends AbstractHttpEntity {

    private static final int OUTPUT_BUFFER_SIZE = 4096;

    private final InputStream content;
    private final long length;

    public InputStreamEntity(final InputStream instream) {
        this(instream, -1);
    }

    public InputStreamEntity(final InputStream instream, final long length) {
        super();
        if (instream == null) {
            throw new IllegalArgumentException("Source input stream may not be null");
        }
        this.content = instream;
        this.length = length;
    }

    @Override public boolean isRepeatable() { return false; }

    /** -1 when the length is unknown, exactly as upstream reports it. */
    @Override public long getContentLength() { return this.length; }

    @Override public InputStream getContent() throws IOException { return this.content; }

    @Override public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        final InputStream instream = this.content;
        try {
            final byte[] buffer = new byte[OUTPUT_BUFFER_SIZE];
            int l;
            if (this.length < 0) {
                // Unknown length: consume until EOF.
                while ((l = instream.read(buffer)) != -1) {
                    outstream.write(buffer, 0, l);
                }
            } else {
                // Known length: consume no more than that many bytes.
                long remaining = this.length;
                while (remaining > 0) {
                    l = instream.read(buffer, 0,
                            (int) Math.min(OUTPUT_BUFFER_SIZE, remaining));
                    if (l == -1) break;
                    outstream.write(buffer, 0, l);
                    remaining -= l;
                }
            }
        } finally {
            instream.close();
        }
    }

    @Override public boolean isStreaming() { return true; }

    /** Deprecated upstream but load-bearing for legacy callers: releasing a
     *  streamed entity closes the underlying stream. AbstractHttpEntity's
     *  no-op would leak it. */
    @Override public void consumeContent() throws IOException {
        this.content.close();
    }
}
