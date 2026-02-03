package ServerFunctionalities;

import ServerFunctionalities.Enums.Os;
import java.util.Map;

public class ServerFunc {
    private static final Os COMP_OS = Os.detect();
    private static final Map<String,String> CMDS = Os.getCmds(COMP_OS);




    public static boolean validateToken(String line, String token) {
        String[] parts = line.trim().split(" ");
        if(parts.length != 2) {
            return false;
        }
        return (parts[0].equals("AUTH") && parts[1].equals(token));
    }


    public static String handleCommand(String line) {
        if (line.split(" ").length != 1)
            return "no prop cmd found";
        try {
            return switch (line) {
                case "SHUTDOWN", "REBOOT", "SLEEP" -> Os.exec(COMP_OS, CMDS.get(line)) ? "SUCCESS" : "FAIL";
                default -> "UNKNOWN COMMAND";
            };
        }catch(Exception e) {
            System.out.println("Error while executing command");
            return "Error while executing command";
        }
    }

    }
