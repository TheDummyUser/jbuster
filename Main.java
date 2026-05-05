import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    static List<String> getLines(String wordList) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(wordList));
            return lines;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("jbuster -h for details");
            return;
        }
        Map<String, String> params = new HashMap<>();

        for (int i = 0; i < args.length; i += 2) {
            String key = args[i];
            String value = args[i + 1];
            params.put(key, value);
        }
        String wordList = params.get("-w");
        String url = params.get("url");

        if (wordList == null || url == null) {
            System.out.println("please provide a correct params");
            return;
        }
        List<String> lines = getLines(wordList);

        if (lines == null) {
            System.out.println(
                "unable to read the file or no lines in the file"
            );
            return;
        }

        for (var line : lines) {
            System.out.println(line);
        }
    }
}
