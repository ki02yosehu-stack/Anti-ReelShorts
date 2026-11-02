package de.robv.android.xposed.callbacks;

/** Compile-only stub. LSPosed supplies the real class at runtime. */
public class XC_LoadPackage {
    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
    }
}
