package com.fyp.floodmonitoring.repository;

import com.fyp.floodmonitoring.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSettingRepository extends JpaRepository<UserSetting, UUID> {

    List<UserSetting> findByUserIdOrderByKeyAsc(UUID userId);

    Optional<UserSetting> findByUserIdAndKey(UUID userId, String key);

    // Idempotently seed a default (disabled) setting row. This native INSERT
    // must not depend on DB features that Hibernate ddl-auto=update doesn't
    // reliably create (and that didn't survive the Neon->Railway migration):
    //   1. `id` default — the @UuidGenerator only fires for JPA entity
    //      persists, NOT native queries, so we supply it with
    //      gen_random_uuid() (PostgreSQL 13+ core). Omitting it trips the
    //      NOT NULL constraint (500 on POST /admin/users).
    //   2. the composite UNIQUE (user_id, key) — `ON CONFLICT (user_id, key)`
    //      needs a matching unique index; if it's absent Postgres throws
    //      "no unique or exclusion constraint matching the ON CONFLICT
    //      specification". WHERE NOT EXISTS needs no constraint and is still
    //      idempotent for one-shot default seeding.
    @Modifying
    @Query(value = """
           INSERT INTO user_settings (id, user_id, key, enabled)
           SELECT gen_random_uuid(), :userId, :key, false
            WHERE NOT EXISTS (
                  SELECT 1 FROM user_settings
                   WHERE user_id = :userId AND key = :key
           )
           """, nativeQuery = true)
    void upsertDefault(UUID userId, String key);

    @Modifying
    @Query("DELETE FROM UserSetting s WHERE s.userId = :userId")
    void deleteByUserId(UUID userId);
}
