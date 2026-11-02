package com.ecarx.sdk.policy;

import android.content.Intent;
public interface IStateAPI {
    void disRecoveryIntent(String str);

    void updateMediaStateRecoveryIntent(String str, Intent intent);
}
