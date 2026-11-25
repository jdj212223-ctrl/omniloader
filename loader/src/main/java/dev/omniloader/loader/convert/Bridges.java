package dev.omniloader.loader.convert;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Loads "version bridges": lists of what Mojang renamed between two Minecraft versions. */
public final class Bridges {
    private Bridges() {}

    public static Map<String, String> load(String from, String to) {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = Bridges.class.getResourceAsStream("/bridges/" + from + "-to-" + to + ".txt")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length == 3 && p[0].equals("c")) out.put(p[1], p[2]);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
