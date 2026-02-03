package ServerFunctionalities;

import ServerFunctionalities.Enums.LoadProp;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class InfoLoader {

    private static final Path path = Path.of("info/info.txt");

    public static String load(LoadProp loadProp) {
        String askedInfo = null;
        try(BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                switch (loadProp) {
                    case TOKEN:
                        if (line.startsWith("TOKEN")) {
                            askedInfo = line.replace("TOKEN :", "").trim();
                            return askedInfo;
                        }
                        break;
                    case ID:
                        if (line.startsWith("ID")) {
                            line = line.replace("ID :", "").trim();
                            if("".equals(line)){
                             askedInfo = UUID.randomUUID().toString();
                             write(LoadProp.ID, askedInfo);
                            }else{
                             askedInfo = line;
                            }
                            return askedInfo;
                        }
                }
            }
            if (askedInfo == null) {
                System.out.println("Error while loading askedInfo(doesnt exists)");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println("Error while loading askedInfo");
            System.exit(1);
        }
        return null;
    }

    public static void write(LoadProp loadProp, String info) {
        try{
            List<String> lines = Files.readAllLines(path);

            switch (loadProp) {
                case TOKEN:
                    break;
                case ID:
                    String id = info.trim();
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).startsWith("ID")) {
                            lines.set(i, "ID : " + id);
                        }
                    }
                    break;
            }

            Files.write(path, lines);

        }catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while writing id to file");
        }
    }
}
