package de.robv.android.xposed;

import java.lang.reflect.Member;

/** Compile-only stub. LSPosed supplies the real class at runtime. */
public final class XposedBridge {
    private XposedBridge() {}

    public static void log(String text) {}

    public static void log(Throwable t) {}

    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
            throws Throwable {
        throw new UnsupportedOperationException("stub");
    }
}
