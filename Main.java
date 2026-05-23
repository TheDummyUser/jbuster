import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpClient.Version;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) {
        if (args.length < 2) {
            help();
            return;
        }
        var params = new HashMap<String, String>();
        var customHeaders = new ArrayList<String>();

        var client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        for (int i = 0; i < args.length; i++) {
            var currentArg = args[i];

            if (currentArg.equals("-H")) {
                if (i + 1 < args.length) {
                    customHeaders.add(args[i + 1]);
                    i++;
                }
            } else if (currentArg.startsWith("-")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    params.put(currentArg, args[i + 1]);
                    i++;
                } else {
                    params.put(currentArg, "true");
                }
            }
        }

        if (params.containsKey("-h")) {
            help();
            return;
        }

        var wordList = params.get("-w");
        var url = params.get("-u");
        int maxThreads = Integer.parseInt(params.getOrDefault("-t", "20"));
        var bouncer = new Semaphore(maxThreads);
        var sizeSkip = new HashSet<Integer>();
        var statusSkip = new HashSet<Integer>();
        var extensions = new ArrayList<String>();
        var httpArgs = params.getOrDefault("-hv", "1.1");
        var prefferedVersion = httpArgs.equals("2")
            ? Version.HTTP_2
            : Version.HTTP_1_1;
        if (wordList == null || url == null) {
            System.err.println("Error: Missing required arguments.");
            System.err.println(
                "Usage: java Main -u <url> -w <wordlist> or check -h"
            );
            return;
        }

        if (params.containsKey("-Ss")) {
            var sizes = params.get("-Ss").split(",");
            for (var strSizes : sizes) {
                try {
                    sizeSkip.add(Integer.parseInt(strSizes.trim()));
                } catch (NumberFormatException e) {
                    System.err.println(
                        "Warning: Invalid size format in -Ss flag: " + strSizes
                    );
                }
            }
        }

        if (params.containsKey("-x")) {
            var statusCodes = params.get("-x").split(",");
            for (var status : statusCodes) {
                try {
                    statusSkip.add(Integer.parseInt(status.trim()));
                } catch (NumberFormatException e) {
                    System.err.println(
                        "Warning: Invalid size format in -x flag: " + status
                    );
                }
            }
        }

        if (params.containsKey("-e")) {
            var exts = params.get("-e").split(",");

            for (var ext : exts) {
                try {
                    extensions.add(ext);
                } catch (Exception e) {
                    System.out.println(
                        "warning: invalid extensions provided in -e flag: " + e
                    );
                }
            }
        }

        long totalWordCount = 0;

        try (var stream = Files.lines(Paths.get(wordList))) {
            totalWordCount = stream.count();
        } catch (Exception e) {
            System.err.println("Could not read wordlist to calculate total.");
            return;
        }

        int multiplyer = extensions.isEmpty() ? 1 : 1 + extensions.size();

        long totalRequests = totalWordCount * multiplyer;

        var completedRequests = new AtomicInteger(0);

        System.out.println(
            "[*] Starting scan: " +
                totalRequests +
                " total requests to make...\n"
        );
        try (
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            var lines = Files.lines(Paths.get(wordList))
        ) {
            lines
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(line ->
                    executor.submit(() -> {
                        try {
                            bouncer.acquire();
                            try {
                                checkUrl(
                                    client,
                                    url,
                                    line,
                                    sizeSkip,
                                    statusSkip,
                                    extensions,
                                    prefferedVersion,
                                    completedRequests,
                                    totalRequests
                                );
                            } finally {
                                bouncer.release();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    })
                );
        } catch (IOException e) {
            System.err.println("file error: " + e.getMessage());
        }
    }

    private static void help() {
        System.out.println(
            """
            -h : help to spil out all the commands
            -w : path to the wordlist
            -u : url link
            -t : max threads, default 20
            -Ss : size skip [-Ss 452,352,600,900] as size of the page
            -x : status skip [-x 301,302,400,401,402]
            -e : extensions [-e php,html,txt,bok] as use need
            -hv : 1.1 or 2, default 1.1
            base example:
            java Main.java -u https://example.com -w wordlist.txt -t 50 -Ss 452 -x 301,302,400,401,402 -hv 2
            """
        );
    }

    private static List<String> urlGen(
        String baseUrl,
        String path,
        List<String> extensions
    ) {
        var urlsToTest = new ArrayList<String>();

        var cleanBase = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        var cleanPath = path.startsWith("/") ? path.substring(1) : path;
        var fullPath = cleanBase + "/" + cleanPath;

        urlsToTest.add(fullPath);
        if (extensions != null && !extensions.isEmpty()) {
            var pathForExtensions = fullPath.endsWith("/")
                ? fullPath.substring(0, fullPath.length() - 1)
                : fullPath;

            for (var ext : extensions) {
                var safeExt = ext.startsWith(".") ? ext : "." + ext;
                urlsToTest.add(pathForExtensions + safeExt);
            }
        }

        return urlsToTest;
    }

    private static void checkUrl(
        HttpClient client,
        String baseUrl,
        String path,
        Set<Integer> sizeSkip,
        Set<Integer> skipStatus,
        List<String> extensions,
        Version prefferedVersion,
        AtomicInteger completedRequests,
        long totalRequests
    ) {
        var allUrls = urlGen(baseUrl, path, extensions);

        for (var fullUrl : allUrls) {
            try {
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("User-Agent", "JBuster-1.0")
                    .version(prefferedVersion)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

                var response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
                );

                int statusCode = response.statusCode();
                int responseSize = response.body().length;

                int current = completedRequests.incrementAndGet();

                if (current % 50 == 0 || current == totalRequests) {
                    double percent = (current * 100.0) / totalRequests;
                    System.out.print(
                        String.format(
                            "\r[~] Progress: %d / %d (%.2f%%)",
                            current,
                            totalRequests,
                            percent
                        )
                    );
                }
                if (sizeSkip.contains(responseSize)) return;

                if (skipStatus.contains(statusCode)) return;

                System.out.printf(
                    "[status code %d] -> %s -> [size %d]\n",
                    statusCode,
                    fullUrl,
                    responseSize
                );
            } catch (Exception e) {
                // Silence connection errors to keep the terminal clean
                completedRequests.incrementAndGet();
            }
        }
    }
}
