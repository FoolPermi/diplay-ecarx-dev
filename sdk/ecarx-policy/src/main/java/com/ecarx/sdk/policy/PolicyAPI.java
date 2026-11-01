package com.ecarx.sdk.policy;

import android.content.Context;
import com.ecarx.sdk.ECarXAPIBase;

/** Compile-only declaration; the head unit supplies the factory and implementation. */
public abstract class PolicyAPI extends ECarXAPIBase implements InternalPolicyAPI {
    protected static final String TAG = "PolicyAPI";

    public static PolicyAPI get(Context context) { throw new UnsupportedOperationException("SDK stub"); }

    @Deprecated
    public static PolicyAPI createPolicyAPI(Context context) { throw new UnsupportedOperationException("SDK stub"); }
}
