import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Main {

    private static void help() {
        System.out.println(
            """
            -h : help to spil out all the commands
            -w : path to the wordlist
            -u : url link
            -t : max threads, default 20

            base example:
            java Main -u https://example.com -w wordlist.txt -t 50
            """
        );
    }

    private static void checkUrl(
        HttpClient client,
        String baseUrl,
        String path
    ) {
        try {
            String fullUrl = baseUrl.endsWith("/")
                ? baseUrl + path
                : baseUrl + "/" + path;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("User-Agent", "JBuster-1.0")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<Void> response = client.send(
                request,
                HttpResponse.BodyHandlers.discarding()
            );

            int statusCode = response.statusCode();
            if (statusCode == 200 || statusCode == 301 || statusCode == 302) {
                System.out.printf("[%d] -> %s\n", statusCode, fullUrl);
            }
        } catch (Exception e) {
            // Silence connection errors to keep the terminal clean
            // System.err.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            help();
            return;
        }
        Map<String, String> params = new HashMap<>();
        // Correct way to build the client
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER) // Ensure this is inside the builder chain
            .build();

        for (int i = 0; i < args.length; i += 2) {
            String key = args[i];
            String value = args[i + 1];
            params.put(key, value);
        }
        String wordList = params.get("-w");
        String url = params.get("-u");
        String threadInput = params.getOrDefault("-t", "20");
        int maxThreads = Integer.parseInt(threadInput);
        Semaphore bouncer = new Semaphore(maxThreads);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        if (wordList == null || url == null) {
            System.out.println("please provide a correct params");
            return;
        }

        try (Stream<String> lines = Files.lines(Paths.get(wordList))) {
            lines
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .forEach(line -> {
                    executor.submit(() -> {
                        try {
                            bouncer.acquire();
                            try {
                                checkUrl(client, url, line);
                            } finally {
                                bouncer.release();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                });
        } catch (IOException e) {
            System.err.println("file error: " + e.getMessage());
        }
        executor.shutdown();
        try {
            // Wait up to 1 hour for all tasks to finish
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
