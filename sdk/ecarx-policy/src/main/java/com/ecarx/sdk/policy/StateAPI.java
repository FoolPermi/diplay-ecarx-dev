package com.ecarx.sdk.policy;

import android.content.Context;
import com.ecarx.sdk.ECarXAPIBase;

/** Compile-only declaration recovered with the Policy module. */
public abstract class StateAPI extends ECarXAPIBase implements IStateAPI {
    public static StateAPI get(Context context) { throw new UnsupportedOperationException("SDK stub"); }

    @Deprecated
    public static StateAPI createStateAPI(Context context) { throw new UnsupportedOperationException("SDK stub"); }
}
