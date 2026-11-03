package java.lang.invoke;
/** Compile-time stub (core-lambda-stubs); d8 desugars every lambda, so this never ships. */
public final class LambdaMetafactory {
    public static final int FLAG_SERIALIZABLE = 1, FLAG_MARKERS = 2, FLAG_BRIDGES = 4;
    public static CallSite metafactory(MethodHandles.Lookup caller, String name, MethodType type,
            MethodType samType, MethodHandle impl, MethodType instType) throws LambdaConversionException { throw new UnsupportedOperationException(); }
    public static CallSite altMetafactory(MethodHandles.Lookup caller, String name, MethodType type, Object... args)
            throws LambdaConversionException { throw new UnsupportedOperationException(); }
}
