package io.hyperfoil.tools.horreum.svc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import io.hyperfoil.tools.horreum.api.data.Banner;
import io.hyperfoil.tools.horreum.mapper.BannerMapper;
import io.hyperfoil.tools.horreum.api.services.BannerService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.entity.BannerDAO;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;

public class BannerServiceImpl implements BannerService {
   private static final Logger log = Logger.getLogger(BannerServiceImpl.class);

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
   public void set(Banner dto) {
      BannerDAO banner = BannerMapper.to(dto);
      BannerDAO previous = getBanner();
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
         String response = responseTemplate.data("hasBanner", hasBanner, "created", banner.created, "title", banner.title, "message", banner.message).render();
         try {
            Files.writeString(Path.of(downtimeResponse.get()), response);
         } catch (IOException e) {
            log.error("Failed to write response file " + downtimeResponse.get(), e);
         }
      }
   }

   @PermitAll
   @Override
   public Banner get() {
     return BannerMapper.from(getBanner());
   }

   private BannerDAO getBanner() {
      @SuppressWarnings("unchecked")
      List<BannerDAO> banners = em.createQuery("SELECT b FROM Banner b WHERE active IS TRUE ORDER BY created DESC").setMaxResults(1).getResultList();
      return banners != null && !banners.isEmpty() ? banners.get(0) : null;
   }
}
