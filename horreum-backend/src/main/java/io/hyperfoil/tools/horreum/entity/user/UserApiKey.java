package io.hyperfoil.tools.horreum.entity.user;

import static jakarta.persistence.GenerationType.SEQUENCE;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import io.hyperfoil.tools.horreum.api.services.UserService;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "userinfo_apikey")
@NamedQueries({
        // fetch all keys that expire on a given day
        @NamedQuery(name = "UserApiKey.expire", query = """
                from UserApiKey where not revoked and (access is null and trunc(creation + (active day), day) = trunc(cast(?1 as localdatetime), day)
                                                                       or trunc(access + (active day), day) = trunc(cast(?1 as localdatetime), day))
                """),
        // fetch all keys that have gone past their expiration date
        @NamedQuery(name = "UserApiKey.pastExpiration", query = """
                from UserApiKey where not revoked and (access is null and trunc(creation + (active day), day) < trunc(cast(?1 as localdatetime), day)
                                                                       or trunc(access + (active day), day) < trunc(cast(?1 as localdatetime), day))
                """),
})
public class UserApiKey extends PanacheEntityBase implements Comparable<UserApiKey> {

    // old authentication tokens are not listed and can't be modified, but are kept around to prevent re-use
    public static long ARCHIVE_AFTER_DAYS = 7;

    @Id
    @SequenceGenerator(name = "apikeyIdGenerator", sequenceName = "userinfo_apikey_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = SEQUENCE, generator = "apikeyIdGenerator")
    public long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "username")
    public UserInfo user;

    @Transient
    private final UUID randomnessSource;

    private final String hash;

    public String name;

    @Enumerated
    public final UserService.KeyType type;

    public Instant creation, access;

    public long active; // number of days after last access that the key remains active

    public boolean revoked;

    public UserApiKey() {
        randomnessSource = null;
        hash = null;
        name = null;
        type = UserService.KeyType.USER;
    }

    public UserApiKey(String name, UserService.KeyType type, Instant creationDate, long valid) {
        randomnessSource = UUID.randomUUID();
        this.name = name;
        this.type = type;
        this.active = valid;
        hash = computeHash(keyString());
        creation = creationDate;
        revoked = false;
    }

    public boolean isArchived(Instant givenDay) {
        return givenDay.isAfter((access == null ? creation : access).plus(active + ARCHIVE_AFTER_DAYS, ChronoUnit.DAYS));
    }

    // calculate the number of days left until expiration (if negative it's the number of days after expiration)
    public long toExpiration(Instant givenDay) {
        return active - ChronoUnit.DAYS.between(access == null ? creation : access, givenDay);
    }

    public String keyString() {
        String typeStr = switch (type) {
            case USER -> "USR";
        };
        return "H" + typeStr + "_" + randomnessSource.toString().replace("-", "_").toUpperCase(); // keep the dashes for quick validation of key format
    }

    // returns the SHA-256 hash of a given key. the hash is what gets sored in the DB, and it's what get compared for authentication
    private static String computeHash(String key) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(key.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            return null; // ignore: SHA-256 should exist
        }
    }

    public static Optional<UserApiKey> findOptional(String key) {
        // validate key structure before computing hash
        if (key.startsWith("H") && Stream.of(4, 13, 18, 23, 28).allMatch(i -> key.charAt(i) == '_')) {
            return UserApiKey.<UserApiKey> find("hash", computeHash(key)).firstResultOptional();
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(this.id, ((UserApiKey) o).id) && Objects.equals(this.hash, ((UserApiKey) o).hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, hash);
    }

    @Override
    public int compareTo(UserApiKey other) {
        return Comparator.<UserApiKey, Instant> comparing(a -> a.creation).thenComparing(a -> a.id).compare(this, other);
    }
}
