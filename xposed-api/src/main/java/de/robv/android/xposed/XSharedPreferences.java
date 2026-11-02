package de.robv.android.xposed;

/** Compile-only stub. LSPosed supplies the real class at runtime. */
public class XSharedPreferences {
    public XSharedPreferences(String packageName, String prefFileName) {}

    /** @return whether the prefs file was made readable. Real LSPosed ignores this. */
    public boolean makeWorldReadable() {
        return false;
    }

    public void reload() {}

    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }
}
