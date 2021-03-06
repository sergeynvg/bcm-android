package com.bcm.messenger.login.logic;

import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.crypto.IdentityKeyUtil;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.crypto.PreKeyUtil;
import com.bcm.messenger.common.jobs.MasterSecretJob;
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirement;
import com.bcm.messenger.common.provider.AmeModuleCenter;

import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.List;


public class RefreshPreKeysJob extends MasterSecretJob {

    private static final String TAG = RefreshPreKeysJob.class.getSimpleName();

    private static final int PREKEY_MINIMUM = 10;

    public RefreshPreKeysJob(Context context, AccountContext accountContext) {
        super(context, accountContext, JobParameters.newBuilder()
                .withGroupId(RefreshPreKeysJob.class.getSimpleName())
                .withRequirement(new NetworkRequirement(context))
                .withRequirement(new MasterSecretRequirement(context, accountContext))
                .withRetryCount(5)
                .create());
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onRun(MasterSecret masterSecret) throws IOException {

        int availableKeys = AmeLoginCore.INSTANCE.getAvailablePreKeys(accountContext);
        if (availableKeys < 0) {
            throw new IOException("fetch prekey params failed");
        }

        if (availableKeys >= PREKEY_MINIMUM && accountContext.isSignedPreKeyRegistered()) {
            Log.w(TAG, "Available keys sufficient: " + availableKeys);
            return;
        }

        List<PreKeyRecord> preKeyRecords = PreKeyUtil.generatePreKeys(context, accountContext);
        IdentityKeyPair identityKey = IdentityKeyUtil.getIdentityKeyPair(accountContext);
        SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, accountContext,identityKey, false);

        Log.w(TAG, "Registering new prekeys...");

        if (!AmeLoginCore.INSTANCE.refreshPreKeys(accountContext, identityKey.getPublicKey(), signedPreKeyRecord, preKeyRecords)) {
            throw new IOException("upload prekey failed");
        }

        PreKeyUtil.setActiveSignedPreKeyId(context, accountContext, signedPreKeyRecord.getId());
        accountContext.setSignedPreKeyRegistered(true);

        JobManager jobManager = AmeModuleCenter.INSTANCE.accountJobMgr(accountContext);
        if (jobManager != null) {
            jobManager.add(new CleanPreKeysJob(context, accountContext));
        }

    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        if (exception instanceof NonSuccessfulResponseCodeException) return false;
        if (exception instanceof PushNetworkException) return true;

        return false;
    }

    @Override
    public void onCanceled() {

    }

}
