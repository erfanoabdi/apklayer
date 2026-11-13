/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package apklayer.host;

import org.robolectric.annotation.internal.DoNotInstrument;

import java.util.Map;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.function.Function;

// apklayer patch: extracted verbatim from libcore java.lang.System; route its log sink locally.
@DoNotInstrument
final class PropertiesWithNonOverrideableDefaults extends Properties {
        PropertiesWithNonOverrideableDefaults(Properties defaults) {
            super(defaults);
        }

        @Override
        public synchronized Object compute(Object key,
                BiFunction<? super Object, ? super Object, ?> remappingFunction) {
            if (defaults == null || !defaults.containsKey(key)) {
                return super.compute(key, remappingFunction);
            } else {
                logE("Ignoring attempt to modify property " + key);
                // No new value is associated with the key, hence null.
                return null;
            }
        }

        @Override
        public synchronized Object computeIfAbsent(Object key,
                Function<? super Object, ?> mappingFunction) {
            if (defaults == null || !defaults.containsKey(key)) {
                return super.computeIfAbsent(key, mappingFunction);
            } else {
                logE("Ignoring attempt to modify property " + key);
                return defaults.get(key);
            }
        }

        @Override
        public synchronized Object computeIfPresent(Object key,
                BiFunction<? super Object, ? super Object, ?> remappingFunction) {
            if (defaults == null || !defaults.containsKey(key)) {
                return super.computeIfPresent(key, remappingFunction);
            } else {
                logE("Ignoring attempt to modify property " + key);
                return defaults.get(key);
            }
        }

        @Override
        public synchronized Object merge(Object key, Object value,
                BiFunction<? super Object, ? super Object, ?> remappingFunction) {
            if (defaults == null || !defaults.containsKey(key)) {
                return super.merge(key, value, remappingFunction);
            } else {
                logE("Ignoring attempt to modify property " + key);
                // No new value is associated with the key, but
                return defaults.get(key);
            }
        }

        @Override
        public Object put(Object key, Object value) {
            if (defaults.containsKey(key)) {
                logE("Ignoring attempt to set property \"" + key +
                        "\" to value \"" + value + "\".");
                return defaults.get(key);
            }

            return super.put(key, value);
        }

        @Override
        public synchronized void putAll(Map<?, ?> m) {
            m.forEach(this::put);
        }

        @Override
        public synchronized Object putIfAbsent(Object key, Object value) {
            if (defaults == null || !defaults.containsKey(key)) {
                return super.putIfAbsent(key, value);
            } else {
                logE("Ignoring attempt to modify property " + key);
                // Returned values is in sync with put(Object, Object).
                return defaults.get(key);
            }
        }

        @Override
        public Object remove(Object key) {
            if (defaults.containsKey(key)) {
                logE("Ignoring attempt to remove property \"" + key + "\".");
                return null;
            }

            return super.remove(key);
        }


        @Override
        public synchronized Object replace(Object key, Object value) {
            if (defaults == null || !defaults.containsKey(key)) {
                return super.replace(key, value);
            } else {
                logE("Ignoring attempt to replace property \"" + key + "\".");
                return defaults.get(key);
            }
        }

        @Override
        public synchronized boolean replace(Object key, Object oldValue, Object newValue) {
            if (defaults == null || !defaults.containsKey(key)) {
                return super.replace(key, oldValue, newValue);
            } else {
                logE("Ignoring attempt to replace property \"" + key + "\".");
                // As it was not changed.
                return false;
            }
        }

    private static void logE(String message) { System.err.println(message); }
}
