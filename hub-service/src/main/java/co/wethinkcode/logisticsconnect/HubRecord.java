package co.wethinkcode.logisticsconnect;

import java.util.List;

/**
 * Matches the shape returned by ingestion-service's GET /hubs. A
 * duplicated copy, not a shared import - each service here is an
 * independent Maven module with no parent pom.
 */
public record HubRecord(
        String hubId,
        String province,
        String sortingCenter,
        Boolean active,
        List<String> notes
) {}
