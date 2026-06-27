package ken5005.kreminder.holiday;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HolidayFetcher {

    // Two URLs tried in order — the Cabinet Office has changed the filename before
    private static final String[] URLS = {
        "https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv",
        "https://www8.cao.go.jp/chosei/shukujitsu/shukujitsu.csv"
    };

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private HolidayFetcher() {}

    /**
     * @return raw MS932 bytes of the holiday CSV
     * @throws IOException if all URLs fail
     */
    public static byte[] fetch() throws IOException {
        for (String url : URLS) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
                HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200) return resp.body();
                System.err.println("HolidayFetcher: HTTP " + resp.statusCode() + " from " + url);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("fetch interrupted", e);
            } catch (Exception e) {
                System.err.println("HolidayFetcher: error from " + url + ": " + e.getMessage());
            }
        }
        throw new IOException("All holiday CSV URLs failed");
    }
}
