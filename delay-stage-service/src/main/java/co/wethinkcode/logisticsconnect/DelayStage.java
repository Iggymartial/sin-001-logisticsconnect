package co.wethinkcode.logisticsconnect;

/**
 * Reused as both the REST response shape (GET/POST /delay-stage/{hubId})
 * and the MQ event payload published to package-status-topic - matches
 * the field names given in the integration contract exactly:
 * {"hubId": "H-501", "stage": 5, "timestamp": "..."}.
 */
public record DelayStage(String hubId, int stage, String timestamp) {}
