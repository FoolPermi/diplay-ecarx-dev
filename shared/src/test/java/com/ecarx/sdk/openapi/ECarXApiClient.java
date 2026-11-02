package com.ecarx.sdk.openapi;

/** GKUI 3.3.9 initialization callback ABI. */
public abstract class ECarXApiClient {
    public interface Callback { void onAPIReady(boolean ready); }
}
