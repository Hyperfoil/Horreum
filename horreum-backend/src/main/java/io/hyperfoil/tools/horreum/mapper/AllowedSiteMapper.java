package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.data.AllowedSiteDAO;
import io.hyperfoil.tools.horreum.api.data.AllowedSite;

public class AllowedSiteMapper {
    public static AllowedSite from(AllowedSiteDAO site) {
        AllowedSite dto = new AllowedSite();
        dto.id = site.id;
        dto.prefix = site.prefix;

        return dto;
    }
}
