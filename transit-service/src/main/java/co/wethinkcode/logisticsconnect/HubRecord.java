package co.wethinkcode.logisticsconnect;

import java.util.List;

public record HubRecord(
        String hubId,
        String province,
        String sortingCenter,
        Boolean active,
        List<String> notes
) {}
