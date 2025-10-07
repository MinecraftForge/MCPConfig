package mcp.client;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import net.minecraft.client.main.Main;

public class Start {
    public static void main(String[] args) throws IOException {
        /*
         * start minecraft game application
         * --version is just used as 'launched version' in snoop data and is required
         * Working directory is used as gameDir if not provided
         */
        int INDEX = 27;
        String assets = findAssets(INDEX);

        Main.main(concat(new String[]{"--version", "mcp", "--accessToken", "0", "--assetsDir", assets, "--assetIndex", Integer.toString(INDEX), "--userProperties", "{}"}, args));
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String findAssets(int index) throws IOException {
        // If we're explicitly told, use it
        String ret = System.getenv("assetDirectory");
        if (ret != null)
            return ret;

        // Minecraft is always a good option.
        File dir = new File(getMCDir(), "assets");
        if (hasIndex(dir, index))
            return dir.getCanonicalPath();

        // MCPConfig repo
        dir = new File(".").getCanonicalFile();
        while (dir != null && !"versions".equals(dir.getName()))
            dir = dir.getParentFile();

        if (dir != null) {
            dir = new File(dir, "../build/assets");
            if (hasIndex(dir, index))
                return dir.getCanonicalPath();
        }

        // Random guess
        return "assets";
    }

    private static boolean hasIndex(File root, int idx) {
        return new File(root, "indexes/" + idx + ".json").exists();
    }

    private static File getMCDir() {
        switch (OS.getCurrent()) {
            case OSX:
                return new File(System.getProperty("user.home") + "/Library/Application Support/minecraft");
            case WINDOWS:
                return new File(System.getenv("APPDATA") + "\\.minecraft");
            case LINUX:
            default:
                return new File(System.getProperty("user.home") + "/.minecraft");
        }
    }

    private enum OS {
        WINDOWS("win"),
        LINUX("linux", "unix"),
        OSX("osx", "mac"),
        UNKNOWN;

        private final String[] keys;

        OS(String... keys) {
            this.keys = keys;
        }

        static OS getCurrent() {
            String prop = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            for (OS os : OS.values()) {
                for (String key : os.keys) {
                    if (prop.contains(key))
                        return os;
                }
            }
            return UNKNOWN;
        }
    }
}
