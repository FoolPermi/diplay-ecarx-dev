package com.ecarx.sdk.policy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
public interface IAudioPolicy {
    public static final String FUNCTION_AUDIO_ASSISTANT = "AUDIO_ASSISTANT";
    public static final String FUNCTION_BLUETOOTH_HFP = "BLUETOOTH_HFP";
    public static final String FUNCTION_NAVIGATION_GUIDE = "NAVIGATION_GUIDE";
    public static final String FUNCTION_NAVIGATION_TURN_HINT = "NAVIGATION_TURN_HINT";
    public static final String FUNCTION_RADIO_AM = "RADIO_AM";
    public static final String FUNCTION_RADIO_FM = "RADIO_FM";
    public static final String FUNCTION_TTS = "TTS";
    public static final int REQUEST_TYPE_CALL = 2;
    public static final int REQUEST_TYPE_RECORD = 3;
    public static final int REQUEST_TYPE_SYSTEM = 1;

    @Retention(RetentionPolicy.SOURCE)
    public @interface AudioFunction {
    }
    public @interface MicRequestType {
    }

    int getAudioStreamTypeForFunction(String str);

    boolean releaseMic(IMicResourceCallback iMicResourceCallback);

    boolean requestMic(@MicRequestType int i, IMicResourceCallback iMicResourceCallback);
}
