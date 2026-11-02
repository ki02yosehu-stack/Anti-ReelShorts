package de.robv.android.xposed;

/** Compile-only stub. LSPosed supplies the real class at runtime. */
public final class XposedHelpers {
    private XposedHelpers() {}

    public static XC_MethodHook.Unhook findAndHookMethod(
            String className,
            ClassLoader classLoader,
            String methodName,
            Object... parameterTypesAndCallback) {
        throw new UnsupportedOperationException("stub");
    }
}
