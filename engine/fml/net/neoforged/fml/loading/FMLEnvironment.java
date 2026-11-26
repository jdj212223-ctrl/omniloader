package net.neoforged.fml.loading;
import net.neoforged.api.distmarker.Dist;
public class FMLEnvironment {
    public static final Dist dist = FMLLoader.getDist();
    public static final boolean production = true;
    public static Dist getDist() { return dist; }
    public static boolean isProduction() { return production; }
}
