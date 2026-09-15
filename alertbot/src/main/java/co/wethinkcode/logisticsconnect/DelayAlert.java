package co.wethinkcode.logisticsconnect;

/** A simulated social media post, triggered when a hub's delay stage crosses the alert threshold. */
public record DelayAlert(String hubId, int stage, String message, String postedAt) {}
