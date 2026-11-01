package com.ecarx.sdk.policy;

import android.annotation.TargetApi;
public interface IPolicyAPI {
    @TargetApi(26)
    IAudioAttributes getAudioAttributes();

    IAudioPolicy getAudioPolicy();

    IStoragePolicy getStoragePolicy();

    IVoiceAssistantPolicy getVoiceAssistantPolicy();
}
