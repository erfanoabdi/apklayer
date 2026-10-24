package org.apache.http.params;

/**
 * Base class for parameter collections, matching the Apache HttpClient 4.0-beta2
 * API that Android shipped in its boot classpath until the package was removed.
 * Apps built against that API extend this class directly (rather than
 * re-implementing every convenience accessor), so the concrete methods below
 * have to behave exactly like the original: each one reads the raw parameter
 * through getParameter() and falls back to the caller's default when it is
 * unset.
 */
public abstract class AbstractHttpParams implements HttpParams {

    protected AbstractHttpParams() {
    }

    @Override
    public long getLongParameter(final String name, long defaultValue) {
        final Object param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return ((Long) param).longValue();
    }

    @Override
    public HttpParams setLongParameter(final String name, long value) {
        setParameter(name, Long.valueOf(value));
        return this;
    }

    @Override
    public int getIntParameter(final String name, int defaultValue) {
        final Object param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return ((Integer) param).intValue();
    }

    @Override
    public HttpParams setIntParameter(final String name, int value) {
        setParameter(name, Integer.valueOf(value));
        return this;
    }

    @Override
    public double getDoubleParameter(final String name, double defaultValue) {
        final Object param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return ((Double) param).doubleValue();
    }

    @Override
    public HttpParams setDoubleParameter(final String name, double value) {
        setParameter(name, Double.valueOf(value));
        return this;
    }

    @Override
    public boolean getBooleanParameter(final String name, boolean defaultValue) {
        final Object param = getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return ((Boolean) param).booleanValue();
    }

    @Override
    public HttpParams setBooleanParameter(final String name, boolean value) {
        setParameter(name, value ? Boolean.TRUE : Boolean.FALSE);
        return this;
    }

    @Override
    public boolean isParameterTrue(final String name) {
        return getBooleanParameter(name, false);
    }

    @Override
    public boolean isParameterFalse(final String name) {
        return !getBooleanParameter(name, false);
    }

}
