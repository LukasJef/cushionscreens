package dev.atrixx.cushionscreens;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Stahuje obrazek/GIF z http(s) URL - viz /cushionscreens url. */
public final class CushionUrlFetch {

    private static final int MAX_BYTES = 20 * 1024 * 1024; // 20 MB
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private CushionUrlFetch() {
    }

    public static byte[] download(String url) throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL: " + e.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IOException("Only http:// and https:// URLs are supported");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();
        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Server returned HTTP " + response.statusCode());
        }

        Optional<String> contentLength = response.headers().firstValue("Content-Length");
        if (contentLength.isPresent()) {
            try {
                if (Long.parseLong(contentLength.get()) > MAX_BYTES) {
                    throw new IOException("File is larger than the " + (MAX_BYTES / (1024 * 1024)) + " MB limit");
                }
            } catch (NumberFormatException ignored) {
            }
        }
        byte[] body = response.body();
        if (body.length > MAX_BYTES) {
            throw new IOException("File is larger than the " + (MAX_BYTES / (1024 * 1024)) + " MB limit");
        }
        if (body.length == 0) {
            throw new IOException("Downloaded file is empty");
        }
        return body;
    }

    /** Sniffuje GIF87a/GIF89a magic bytes - spolehlivejsi nez spoleha na URL priponu. */
    public static boolean looksLikeGif(byte[] data) {
        return data.length >= 6
            && data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
            && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }
}
