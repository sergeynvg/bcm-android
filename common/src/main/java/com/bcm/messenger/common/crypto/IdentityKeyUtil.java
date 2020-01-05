/**
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import androidx.annotation.NonNull;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.Base64;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.util.ByteUtil;

import java.io.IOException;

/**
 * Utility class for working with identity keys.
 *
 * @author Moxie Marlinspike
 */

public class IdentityKeyUtil {

    private static final String TAG = IdentityKeyUtil.class.getSimpleName();

    private static final String IDENTITY_PUBLIC_KEY_CIPHERTEXT_LEGACY_PREF = "pref_identity_public_curve25519";
    private static final String IDENTITY_PRIVATE_KEY_CIPHERTEXT_LEGACY_PREF = "pref_identity_private_curve25519";

    public static final String IDENTITY_PUBLIC_KEY_PREF = "pref_identity_public_v3";
    public static final String IDENTITY_PRIVATE_KEY_PREF = "pref_identity_private_v3";

    public static boolean hasIdentityKey(AccountContext accountContext) {
        if (!AMELogin.INSTANCE.isLogin()){
            return false;
        }
        Context context = AppContextHolder.APP_CONTEXT;
        SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.getPreferencesName(accountContext), 0);

        return
                preferences.contains(IDENTITY_PUBLIC_KEY_PREF) &&
                        preferences.contains(IDENTITY_PRIVATE_KEY_PREF);
    }

    public static @NonNull
    IdentityKey getIdentityKey(@NonNull AccountContext accountContext) {
        if (!hasIdentityKey(accountContext)) {
            throw new IllegalStateException("There isn't one!");
        }

        try {
            byte[] publicKeyBytes = Base64.decode(retrieve(accountContext, IDENTITY_PUBLIC_KEY_PREF));
            return new IdentityKey(publicKeyBytes, 0);
        } catch (IOException | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public static @NonNull
    IdentityKeyPair getIdentityKeyPair(@NonNull AccountContext accountContext) {
        if (!hasIdentityKey(accountContext)) {
            throw new IllegalStateException("There isn't one!");
        }

        try {
            IdentityKey publicKey = getIdentityKey(accountContext);
            ECPrivateKey privateKey = Curve.decodePrivatePoint(Base64.decode(retrieve(accountContext, IDENTITY_PRIVATE_KEY_PREF)));

            return new IdentityKeyPair(publicKey, privateKey);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static ECKeyPair rebuildIdentityKeys(@NonNull AccountContext accountContext, byte[] privateKeyBytes) throws Exception {

        ECPrivateKey ecPrivateKey = Curve.decodePrivatePoint(privateKeyBytes);
        byte[] publicKeyBytes = ByteUtil.combine(new byte[]{Curve.DJB_TYPE}, BCMPrivateKeyUtils.INSTANCE.generatePublicKey(ecPrivateKey.serialize()));
        save(accountContext, IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(publicKeyBytes));
        save(accountContext, IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(ecPrivateKey.serialize()));

        return new ECKeyPair(Curve.decodePoint(publicKeyBytes, 0), ecPrivateKey);
    }

    public static ECKeyPair generateIdentityKeys(AccountContext accountContext) {
        ECKeyPair djbKeyPair = BCMPrivateKeyUtils.INSTANCE.generateKeyPair();
        IdentityKey djbIdentityKey = new IdentityKey(djbKeyPair.getPublicKey());
        ECPrivateKey djbPrivateKey = djbKeyPair.getPrivateKey();

        save(accountContext, IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(djbIdentityKey.serialize()));
        save(accountContext, IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(djbPrivateKey.serialize()));

        return djbKeyPair;
    }

    public static void migrateIdentityKeys(@NonNull AccountContext accountContext,
                                           @NonNull MasterSecret masterSecret) {
        if (!hasIdentityKey(accountContext)) {
            if (hasLegacyIdentityKeys(accountContext)) {
                IdentityKeyPair legacyPair = getLegacyIdentityKeyPair(accountContext, masterSecret);

                save(accountContext, IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(legacyPair.getPublicKey().serialize()));
                save(accountContext, IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(legacyPair.getPrivateKey().serialize()));

                delete(accountContext, IDENTITY_PUBLIC_KEY_CIPHERTEXT_LEGACY_PREF);
                delete(accountContext, IDENTITY_PRIVATE_KEY_CIPHERTEXT_LEGACY_PREF);
            } else {
                generateIdentityKeys(accountContext);
            }
        }
    }

    private static boolean hasLegacyIdentityKeys(@NonNull AccountContext accountContextt) {
        return
                retrieve(accountContextt, IDENTITY_PUBLIC_KEY_CIPHERTEXT_LEGACY_PREF) != null &&
                        retrieve(accountContextt, IDENTITY_PRIVATE_KEY_CIPHERTEXT_LEGACY_PREF) != null;
    }

    private static IdentityKeyPair getLegacyIdentityKeyPair(@NonNull AccountContext accountContext,
                                                            @NonNull MasterSecret masterSecret) {
        try {
            MasterCipher masterCipher = new MasterCipher(masterSecret);
            byte[] publicKeyBytes = Base64.decode(retrieve(accountContext, IDENTITY_PUBLIC_KEY_CIPHERTEXT_LEGACY_PREF));
            IdentityKey identityKey = new IdentityKey(publicKeyBytes, 0);
            ECPrivateKey privateKey = masterCipher.decryptKey(Base64.decode(retrieve(accountContext, IDENTITY_PRIVATE_KEY_CIPHERTEXT_LEGACY_PREF)));

            return new IdentityKeyPair(identityKey, privateKey);
        } catch (IOException | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    private static String retrieve(@NonNull AccountContext accountContext, String key) {
        Context context = AppContextHolder.APP_CONTEXT;
        SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.getPreferencesName(accountContext), 0);
        return preferences.getString(key, null);
    }

    private static void save(@NonNull AccountContext accountContext, String key, String value) {
        Context context = AppContextHolder.APP_CONTEXT;
        SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.getPreferencesName(accountContext), 0);
        Editor preferencesEditor = preferences.edit();

        preferencesEditor.putString(key, value);
        if (!preferencesEditor.commit()) {
            throw new AssertionError("failed to save identity key/value to shared preferences");
        }
    }

    private static void delete(@NonNull AccountContext accountContext, String key) {
        Context context = AppContextHolder.APP_CONTEXT;
        context.getSharedPreferences(MasterSecretUtil.getPreferencesName(accountContext), 0).edit().remove(key).apply();
    }

}
