package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.BannerDAO;
import io.hyperfoil.tools.horreum.api.data.Banner;

public class BannerMapper {

    public static Banner from(BannerDAO b) {
        Banner dto = new Banner();
        dto.id = b.id;
        dto.active = b.active;
        dto.created = b.created;
        dto.title = b.title;
        dto.message = b.message;
        dto.severity = b.severity;

        return dto;
    }

    public static BannerDAO to(Banner dto) {
       BannerDAO b = new BannerDAO();
        b.id = dto.id;
        b.active = dto.active;
        b.created = dto.created;
        b.title = dto.title;
        b.message = dto.message;
        b.severity = dto.severity;

        return b;
    }
}
