package co.wethinkcode.logisticsconnect;

import java.io.InputStream;
import java.util.List;

import io.javalin.Javalin;

public class IngestionServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7050);

        app.get("/health", ctx -> ctx.result("OK"));

        // Cleaned once at startup and held in memory - this is a
        // scaffold exercise over a static CSV, not a live-updating
        // source, so re-cleaning on every request adds cost for no
        // benefit.
        List<HubRecord> cleanedHubs = loadCleanedHubs();

        app.get("/hubs", ctx -> ctx.json(cleanedHubs));
    }

    private static List<HubRecord> loadCleanedHubs() {
        try (InputStream csv = IngestionServiceApp.class.getResourceAsStream("/hubs-global.csv")) {
            if (csv == null) {
                throw new IllegalStateException("hubs-global.csv not found on classpath");
            }
            return new HubCsvCleaner().clean(csv);
        } catch (Exception e) {
            // Fail loudly and immediately at startup rather than serving
            // an empty/broken /hubs endpoint that looks like it's working.
            throw new RuntimeException("Failed to load and clean hubs-global.csv", e);
        }
    }
}
