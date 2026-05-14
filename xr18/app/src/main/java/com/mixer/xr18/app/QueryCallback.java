package com.mixer.xr18.app;

import com.mixer.xr18.lib.domain.model.ChannelState;

/**
 * Callback for query results.
 * Updated to pass ChannelState array instead of String.
 */
public interface QueryCallback {
    void onResult(ChannelState[] result);
}