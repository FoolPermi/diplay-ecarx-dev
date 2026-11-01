package com.ecarx.sdk;

import android.content.Context;
import com.ecarx.sdk.log.ILog;
import com.ecarx.sdk.log.LogProxy;
import com.ecarx.sdk.openapi.ECarXApiClient;

/** Compile-only declaration recovered from the target firmware's GKUI SDK 3.3.9. */
public class ECarXAPIBase {
    protected static final int ERROR_CODE = -1;
    public static final int SUPPORT_AUTH_VERSION = 330;
    public static final String VERSION;
    public static final int VERSION_INT;
    public static final int REVERSION_INT;
    protected LogProxy L;

    // These fields are initialized at runtime in the firmware, so do not inline their values.
    static {
        VERSION = "3.3.9";
        VERSION_INT = 339;
        REVERSION_INT = 2;
    }

    public final String getVersion() { throw new UnsupportedOperationException("SDK stub"); }
    public final int getVersionInt() { throw new UnsupportedOperationException("SDK stub"); }
    public final int getReversionInt() { throw new UnsupportedOperationException("SDK stub"); }
    public void init(Context context, ECarXApiClient.Callback callback) { throw new UnsupportedOperationException("SDK stub"); }
    public void release() { throw new UnsupportedOperationException("SDK stub"); }
    public void setLogEnable(boolean enable) { throw new UnsupportedOperationException("SDK stub"); }
    public void setLogLevel(int level) { throw new UnsupportedOperationException("SDK stub"); }
    public void setLogImpl(ILog logImpl) { throw new UnsupportedOperationException("SDK stub"); }
}
