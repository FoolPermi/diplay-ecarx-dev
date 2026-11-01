package com.ecarx.sdk.policy;

/** Observed runtime ABI, independent of the retained SDK module. */
public interface IAudioPolicy {
    int getAudioStreamTypeForFunction(String function);
    boolean requestMic(int requestType, IMicResourceCallback callback);
    boolean releaseMic(IMicResourceCallback callback);
}
