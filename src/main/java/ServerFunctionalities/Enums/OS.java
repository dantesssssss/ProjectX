package ServerFunctionalities.Enums;

import java.util.Map;

public enum OS {
    WINDOWS, MAC, LINUX, OTHER;

    public static OS detect() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return WINDOWS;
        if (os.contains("mac")) return MAC;
        if (os.contains("nix") || os.contains("nux")) return LINUX;
        return OTHER;
    }
    public static Map<String, String> getCmds(OS os) {
        return switch (os) {
            case WINDOWS -> Map.of(
                    "SHUTDOWN", "shutdown /s /t 0",
                    "REBOOT", "shutdown /r /t 0",
                    "SLEEP", "rundll32.exe powrprof.dll,SetSuspendState 0,1,0"
            );
            case MAC -> Map.of(
                    "SHUTDOWN", "shutdown",
                    "REBOOT", "restart",
                    "SLEEP", "sleep"
            );
            case LINUX -> Map.of(
                    "SHUTDOWN", "systemctl poweroff",
                    "REBOOT", "systemctl reboot",
                    "SLEEP", "systemctl suspend"
            );
            case OTHER -> null;
        };
    }

    public static boolean exec(OS os,String command) throws Exception {
        ProcessBuilder pb;

        if (os == WINDOWS) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        }else if(os == MAC) {
            pb = new ProcessBuilder(
                    "osascript",
                    "-e",
                    "tell application \"System Events\" to restart".replace("restart", command)
            );

        }else{
            pb = new ProcessBuilder("/bin/sh", "-c", command);
        }

        pb.inheritIO().start();
        return true;
    }
}
