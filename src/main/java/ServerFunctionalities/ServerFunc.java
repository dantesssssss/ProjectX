package ServerFunctionalities;

import ServerFunctionalities.Enums.LOADPROP;
import ServerFunctionalities.Enums.OS;
import java.util.Map;

public class ServerFunc {
    private static final String ID = "";
    private static final OS COMP_OS = OS.detect();
    private static final Map<String,String> CMDS = OS.getCmds(COMP_OS);



    public static boolean validateToken(String line, String token) {
        String[] parts = line.trim().split(" ");
        if (parts.length != 2 || !parts[0].equals("AUTH") || !parts[1].equals(token)) {
            return false;
        }
        return true;
    }

    public static String handleCommand(String line) {
        if (line.split(" ").length != 1)
            return "no prop cmd found";
        try {
            switch (line) {
                case "SHUTDOWN":
                case "REBOOT":
                case "SLEEP":
                    return OS.exec(COMP_OS,CMDS.get(line)) ? "SUCCESS" : "FAIL";
                default:
                    return "UNKNOWN COMMAND";
            }
        }catch(Exception e) {
            System.out.println("Error while executing command");
            return "Error while executing command";
        }
    }

    }
