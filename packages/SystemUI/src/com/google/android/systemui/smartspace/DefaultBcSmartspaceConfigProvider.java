package com.google.android.systemui.smartspace;

import com.android.systemui.plugins.BcSmartspaceConfigPlugin;

public final class DefaultBcSmartspaceConfigProvider implements BcSmartspaceConfigPlugin {
    public final boolean isDefaultDateWeatherDisabled() {
        return false;
    }

    public final boolean isSwipeEventLoggingEnabled() {
        return false;
    }

    public final boolean isViewPager2Enabled() {
        return false;
    }
}
