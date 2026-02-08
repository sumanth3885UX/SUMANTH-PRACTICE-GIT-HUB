package com.example.boj;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public final class BojMarketSummaryClient {

    private static final URI ENDPOINT =
            URI.create("https://boj.org.jm/CounterRates/market_summary/read.php");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        MarketSummaryResponse data = new BojMarketSummaryClient().fetch();

        String latest = data.latestDate()
                .orElseThrow(() -> new IllegalStateException("No date key found in API response."));
        System.out.println("Latest date: " + latest);
        System.out.println("--- ALL currencies on " + latest + " ---");

        data.printAllCurrencies(latest);
    }

    public MarketSummaryResponse fetch() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("HTTP " + res.statusCode() + " from endpoint: " + res.body());
        }

        Map<String, Object> root = mapper.readValue(res.body(), new TypeReference<>() {});
        return MarketSummaryResponse.fromRaw(root);
    }

    public record Rates(BigDecimal buyRate, BigDecimal sellRate, BigDecimal notes, BigDecimal coins) {

        static Rates fromMap(Map<?, ?> m) {
            return new Rates(
                    parseDecimal(m.get("buyrate")),
                    parseDecimal(m.get("sellrate")),
                    parseDecimal(m.get("notes")),
                    parseDecimal(m.get("coins"))
            );
        }

        private static BigDecimal parseDecimal(Object v) {
            if (v == null) return null;
            String s = v.toString().trim().replace("\r", "").replace("\n", "").trim();
            if (s.isEmpty()) return null;
            if (s.startsWith(".")) s = "0" + s;
            return new BigDecimal(s);
        }
    }

    public static final class MarketSummaryResponse {
        private final Map<String, Object> raw;

        private MarketSummaryResponse(Map<String, Object> raw) {
            this.raw = raw;
        }

        static MarketSummaryResponse fromRaw(Map<String, Object> raw) {
            return new MarketSummaryResponse(raw);
        }

        Optional<String> latestDate() {
            return raw.keySet().stream()
                    .filter(k -> k.matches("\\d{4}-\\d{2}-\\d{2}"))
                    .max(String::compareTo);
        }

        Optional<Rates> getRates(String date, String currencyName) {
            Object dateObj = raw.get(date);
            if (!(dateObj instanceof Map<?, ?> dateMap)) return Optional.empty();

            Object currencyObj = dateMap.get(currencyName);
            if (!(currencyObj instanceof Map<?, ?> currencyMap)) return Optional.empty();

            Object inner = currencyMap.get(currencyName);
            if (!(inner instanceof Map<?, ?> ratesMap)) return Optional.empty();

            return Optional.of(Rates.fromMap(ratesMap));
        }

        void printAllCurrencies(String date) {
            Object dateObj = raw.get(date);
            if (!(dateObj instanceof Map<?, ?> dateMap)) {
                System.out.println("(No data for date " + date + ")");
                return;
            }

            for (Object currencyKey : dateMap.keySet()) {
                String currencyName = String.valueOf(currencyKey);
                Optional<Rates> r = getRates(date, currencyName);
                if (r.isPresent()) {
                    Rates rates = r.get();
                    System.out.println(currencyName + " => buy=" + rates.buyRate()
                            + ", sell=" + rates.sellRate()
                            + ", notes=" + rates.notes()
                            + ", coins=" + rates.coins());
                }
            }
        }
    }
}
