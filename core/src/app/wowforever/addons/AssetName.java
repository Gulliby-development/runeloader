package app.wowforever.addons;

import java.util.regex.Pattern;

/** Release asset names that are not addons. The token list matches is_app_binary in runeloader-index indexer/forever.py. */
final class AssetName {
    private AssetName() {}

    private static final Pattern BINARY = Pattern.compile(
            "(^|[-_. ])(win(dows)?|win64|win32|linux|mac(os)?|osx|darwin|x64|x86(_64)?|amd64|arm64|aarch64|setup|installer|portable|candidate)([-_. ]|$)",
            Pattern.CASE_INSENSITIVE);

    /** True for zips of desktop programs (simulators, launchers) rather than addons. */
    static boolean isAppBinary(String name) {
        return name != null && BINARY.matcher(name.replaceFirst("(?i)\\.zip$", "")).find();
    }
}
