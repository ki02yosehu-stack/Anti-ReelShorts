package de.robv.android.xposed;

/** Compile-only stub. LSPosed supplies the real class at runtime. */
public abstract class XC_MethodReplacement extends XC_MethodHook {
    public XC_MethodReplacement() {}

    public XC_MethodReplacement(int priority) {}

    @Override
    protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {}

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;
}
