package com.termux.app.launcher.animation;

import static android.content.Intent.EXTRA_COMPONENT_NAME;
import static android.content.Intent.EXTRA_USER;

import static org.junit.Assert.assertNull;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.Process;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class GestureNavContractCompatTest {

    @Test
    public void fromIntent_returnsNullWhenNoContractBundle() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        assertNull(GestureNavContractCompat.fromIntent(intent));
    }

    @Test
    public void fromIntent_returnsNullForMissingReplyTo() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        Bundle contract = new Bundle();
        contract.putParcelable(EXTRA_COMPONENT_NAME, new ComponentName("pkg", "pkg.Main"));
        contract.putParcelable(EXTRA_USER, Process.myUserHandle());
        contract.putParcelable(GestureNavContractCompat.EXTRA_REMOTE_CALLBACK, Message.obtain());
        intent.putExtra(GestureNavContractCompat.EXTRA_GESTURE_CONTRACT, contract);

        assertNull(GestureNavContractCompat.fromIntent(intent));
    }
}
