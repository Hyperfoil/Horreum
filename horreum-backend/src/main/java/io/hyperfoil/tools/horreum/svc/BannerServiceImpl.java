package io.hyperfoil.tools.horreum.svc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.api.data.Banner;
import io.hyperfoil.tools.horreum.api.internal.services.BannerService;
import io.hyperfoil.tools.horreum.entity.BannerDAO;
import io.hyperfoil.tools.horreum.mapper.BannerMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.logging.Log;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

public class BannerServiceImpl implements BannerService {

    @ConfigProperty(name = "horreum.downtime.response")
    Optional<String> downtimeResponse;

    @Location("horreum_down")
    Template responseTemplate;

    @Inject
    EntityManager em;

    @Inject
    TimeService timeService;

    @RolesAllowed(Roles.ADMIN)
    @WithRoles
    @Transactional
    @Override
    public void setBanner(Banner dto) {
        BannerDAO banner = BannerMapper.to(dto);
        BannerDAO previous = getBannerDAO();
        if (previous != null) {
            previous.active = false;
            em.merge(previous);
        }
        boolean hasBanner = false;
        if (!"none".equals(banner.severity)) {
            banner.id = null;
            banner.created = timeService.now();
            banner.active = true;
            hasBanner = true;
            em.persist(banner);
        }
        if (downtimeResponse.isPresent()) {
            String response = responseTemplate
                    .data("hasBanner", hasBanner, "created", banner.created, "title", banner.title, "message", banner.message)
                    .render();
            try {
                Files.writeString(Path.of(downtimeResponse.get()), response);
            } catch (IOException e) {
                Log.errorf(e, "Failed to write response file %s", downtimeResponse.get());
            }
        }
    }

    @PermitAll
    @Override
    public Banner getBanner() {
        BannerDAO bannerDAO = getBannerDAO();
        return bannerDAO == null ? null : BannerMapper.from(bannerDAO);
    }

    private BannerDAO getBannerDAO() {
        List<BannerDAO> banners = BannerDAO.list("active=?1 ORDER BY created DESC", true);
        return banners != null && !banners.isEmpty() ? banners.get(0) : null;
    }
}
