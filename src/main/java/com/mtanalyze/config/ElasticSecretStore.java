/*
 * Copyright 2026 Centerscout GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mtanalyze.config;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;

import java.util.prefs.Preferences;

/**
 * Stores the Elasticsearch basic-auth password in the OS credential store -- Windows
 * Credential Manager, macOS Keychain, or the Freedesktop Secret Service/KWallet on Linux --
 * via <a href="https://github.com/javakeyring/java-keyring">java-keyring</a>, instead of in
 * plain text in {@link Preferences} like the rest of this app's settings.
 * <p>
 * If no OS keyring backend is available (or writing to it fails for any reason), this falls
 * back to the same plain-text preference the app used before, so it keeps working
 * everywhere. That preference key also holds a pre-existing plain-text password from before
 * this store existed; {@link #get(Preferences)} migrates it into the keyring the first time
 * it is read.
 */
public final class ElasticSecretStore {

    private static final String SERVICE = "MT Analyze";
    private static final String ACCOUNT = "elasticsearch";
    private static final String PLAINTEXT_PREF_KEY = "elastic_password";

    private ElasticSecretStore() {}

    public static String get(Preferences prefs) {
        try (Keyring keyring = Keyring.create()) {
            return getFromKeyringOrMigrate(keyring, prefs);
        } catch (Exception noBackend) {
            // no OS keyring backend on this platform, or it failed to open
            return prefs.get(PLAINTEXT_PREF_KEY, "");
        }
    }

    private static String getFromKeyringOrMigrate(Keyring keyring, Preferences prefs) {
        try {
            return keyring.getPassword(SERVICE, ACCOUNT);
        } catch (PasswordAccessException notFound) {
            String legacy = prefs.get(PLAINTEXT_PREF_KEY, "");
            if (!legacy.isEmpty()) migrateLegacyPassword(keyring, prefs, legacy);
            return legacy;
        }
    }

    private static void migrateLegacyPassword(Keyring keyring, Preferences prefs, String legacy) {
        try {
            keyring.setPassword(SERVICE, ACCOUNT, legacy);
            prefs.remove(PLAINTEXT_PREF_KEY);
        } catch (PasswordAccessException ignored) {
            // keyring rejected the write -- keep the plain-text value for now
            // and retry the migration next time this is read
        }
    }

    public static void save(Preferences prefs, String password) {
        String value = password == null ? "" : password;
        try (Keyring keyring = Keyring.create()) {
            if (value.isEmpty()) {
                deleteFromKeyring(keyring);
            } else {
                keyring.setPassword(SERVICE, ACCOUNT, value);
            }
            prefs.remove(PLAINTEXT_PREF_KEY);
        } catch (Exception noBackend) {
            prefs.put(PLAINTEXT_PREF_KEY, value);
        }
    }

    private static void deleteFromKeyring(Keyring keyring) {
        try {
            keyring.deletePassword(SERVICE, ACCOUNT);
        } catch (PasswordAccessException ignored) {
            // nothing was stored
        }
    }
}
