package com.ecarx.sdk.policy;

import android.content.Context;
import com.ecarx.sdk.openapi.ECarXApiClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test implementation of the recovered GKUI contract, disabled for unrelated tests. */
public final class PolicyAPI {
    private static final PolicyAPI INSTANCE = new PolicyAPI();
    public static final AudioPolicy audio = new AudioPolicy();
    public static boolean enabled;
    public static boolean initialized, autoReady = true, readyResult = true, failInit;
    public static int initCalls, audioLookups;
    private static ECarXApiClient.Callback initCallback;
    public static PolicyAPI get(Context context) { return enabled ? INSTANCE : null; }
    public void init(Context context, ECarXApiClient.Callback callback) {
        initCalls++;
        audio.events.add("init");
        if (failInit) throw new IllegalStateException("Policy service unavailable");
        initCallback = callback;
        if (autoReady) deliverReady(readyResult);
    }
    public static void deliverReady(boolean ready) {
        initialized = ready;
        if (initCallback != null) initCallback.onAPIReady(ready);
    }
    public IAudioPolicy getAudioPolicy() {
        audioLookups++;
        audio.events.add("getAudioPolicy");
        return audio;
    }

    public static final class AudioPolicy implements IAudioPolicy {
        public boolean reject, abort, declineRelease, deferGrant, unsupported;
        public int requests, releases, requestType;
        public IMicResourceCallback held;
        public final List<String> events = new CopyOnWriteArrayList<>();

        public void reset() {
            enabled = initialized = failInit = reject = abort = declineRelease = deferGrant = unsupported = false;
            autoReady = readyResult = true;
            initCalls = audioLookups = 0;
            initCallback = null;
            requests = releases = requestType = 0;
            held = null;
            events.clear();
        }
        public int getAudioStreamTypeForFunction(String function) { return 0; }
        public boolean requestMic(int type, IMicResourceCallback callback) {
            events.add("requestMic");
            requests++;
            requestType = type;
            // The real wrapper exists before readiness but has no remote service and declines.
            if (!initialized || reject || unsupported) return false;
            held = callback;
            if (abort) callback.onAbort(type); else if (!deferGrant) callback.onResume();
            return true;
        }
        public boolean releaseMic(IMicResourceCallback callback) {
            if (held != callback) throw new AssertionError("Released a different callback");
            events.add("releaseMic");
            releases++;
            if (!declineRelease) held = null;
            return !declineRelease;
        }
    }
}
