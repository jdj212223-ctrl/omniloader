package cpw.mods.modlauncher.log;
public final class TransformingThrowablePatternConverter {
    private TransformingThrowablePatternConverter() {}
    public static String generateEnhancedStackTrace(Throwable t) {
        java.io.StringWriter w = new java.io.StringWriter(); t.printStackTrace(new java.io.PrintWriter(w)); return w.toString();
    }
}
