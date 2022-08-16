/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.display;

import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.webkit.AirshipWebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Display arguments.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DisplayArgs {
    private final LayoutInfo payload;
    private final ThomasListener listener;
    private final Factory<AirshipWebViewClient> webViewClientFactory;
    private final ImageCache imageCache;

    public DisplayArgs(
        @NonNull LayoutInfo payload,
        @Nullable ThomasListener listener,
        @Nullable Factory<AirshipWebViewClient> webViewClientFactory,
        @Nullable ImageCache imageCache
    ) {
        this.payload = payload;
        this.listener = listener;
        this.webViewClientFactory = webViewClientFactory;
        this.imageCache = imageCache;
    }

    @Nullable
    public ThomasListener getListener() {
        return listener;
    }

    @NonNull
    public LayoutInfo getPayload() {
        return payload;
    }

    @Nullable
    public ImageCache getImageCache() {
        return imageCache;
    }

    @Nullable
    public Factory<AirshipWebViewClient> getWebViewClientFactory() {
        return webViewClientFactory;
    }

}
