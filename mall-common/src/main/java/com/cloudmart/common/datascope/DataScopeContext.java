package com.cloudmart.common.datascope;

public final class DataScopeContext {

    private static final ThreadLocal<DataScopeResult> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {}

    public static void set(DataScopeResult result) {
        HOLDER.set(result);
    }

    public static DataScopeResult get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
