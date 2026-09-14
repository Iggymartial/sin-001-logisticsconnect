package co.wethinkcode.logisticsconnect;

public record EtaResponse(
        String hubId,
        String province,
        String sortingCenter,
        int delayStage,
        int etaEarliestHours,
        int etaLatestHours,
        String generatedAt
) {}
