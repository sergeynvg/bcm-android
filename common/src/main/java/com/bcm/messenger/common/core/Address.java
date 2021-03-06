package com.bcm.messenger.common.core;


import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.utils.GroupUtil;
import com.bcm.messenger.utility.EncryptUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;

import org.whispersystems.signalservice.internal.util.Base58;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Address implements Parcelable, Comparable<Address>, NotGuard {

    public static final Parcelable.Creator<Address> CREATOR = new Parcelable.Creator<Address>() {
        @Override
        public Address createFromParcel(Parcel in) {
            return new Address(in);
        }

        @Override
        public Address[] newArray(int size) {
            return new Address[size];
        }
    };

    private static final int PHONE_LIMIT = 20;
    private static final String UNKNOWN_STRING = "Unknown";
    private static final Pattern PUBLIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+");
    private static final Pattern emailPattern = android.util.Patterns.EMAIL_ADDRESS;

    public static final Address UNKNOWN = new Address(new AccountContext(UNKNOWN_STRING, "", ""), UNKNOWN_STRING);

    private static final String TAG = "Address";

    public static Boolean isUid(String uid) {
        if (uid.length() == 34 || uid.length() == 33) {
            try {
                return Base58.decodeChecked(uid).length == 21;
            } catch (Throwable e) {
                ALog.e(TAG, "isUid", e);
            }
        }
        return false;
    }

    @NonNull
    private final AccountContext context;
    @NonNull
    private final String address;

    private Boolean isGroup = null;
    private Boolean isPublicKey = null;
    private Boolean isEmail = null;

    private Address(@NonNull AccountContext context, @NonNull String address) {
        this.context = context;
        this.address = address;
    }

    public Address(Parcel in) {
        this.context = (AccountContext)in.readSerializable();
        this.address = in.readString();
    }

    public static Address from(@NonNull AccountContext context, @NonNull String serialized) {
        return new Address(context, serialized);
    }

    public boolean isUnknown() {
        return TextUtils.equals(address, UNKNOWN_STRING) && TextUtils.equals(context.getUid(), UNKNOWN_STRING);
    }

    /**
     * address
     * @return
     */
    public boolean isCurrentLogin() {
        return context.isLogin() && context.getUid().equals(address);
    }

    /**
     * 
     * @return
     */
    public boolean isGroup() {
        if (isGroup == null) {
            isGroup = GroupUtil.isTTGroup(address) || GroupUtil.isEncodedGroup(address);
        }
        return isGroup;
    }

    /**
     * bcm
     * @return
     */
    public boolean isNewGroup() {
        return GroupUtil.isTTGroup(address);
    }

    public boolean isMmsGroup() {
        return GroupUtil.isMmsGroup(address);
    }

    public boolean isEmail() {
        if (isEmail == null) {
            Matcher matcher = emailPattern.matcher(address);
            isEmail = matcher.matches();
        }
        return isEmail;
    }

    /**
     * 
     * @return
     */
    public boolean isIndividual() {
        return !isGroup() && !isEmail();
    }

    /**
     * UID
     * @return
     */
    public boolean isPublicKey() {
        if (isPublicKey == null) {
            isPublicKey = address.length() > PHONE_LIMIT && !isGroup() && !isEmail() && PUBLIC_PATTERN.matcher(address).find();
        }
        return isPublicKey;
    }

    public @NonNull
    String toGroupString() {
        if (!isGroup()) {
            return address;
        }
        return address;
    }

    @Override
    public String toString() {
        return address;
    }

    public String serialize() {
        return address;
    }

    public AccountContext context() {
        return context;
    }

    public String format() {
        if (isPublicKey()) {
            return address.substring(0, 9);
        }
        return address;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !(other instanceof Address)) {
            return false;
        }

        Address oa = (Address)other;
        return context.equals(oa.context) && address.equals(oa.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context, address);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(context);
        dest.writeString(address);
    }

    @Override
    public int compareTo(@NonNull Address other) {
        int result = context.compareTo(other.context);
        if (result == 0) {
            return address.compareTo(other.address);
        }
        return result;
    }
}
