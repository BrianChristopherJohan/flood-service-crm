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

    // NOTE: this native INSERT must supply `id` itself. The @UuidGenerator on
    // the UserSetting entity only fires for JPA entity persists — NOT for
    // native queries — so omitting `id` here inserts NULL and trips the
    // NOT NULL constraint (500 on POST /admin/users) on any schema without a
    // DB-level default (e.g. the Hibernate ddl-auto schema). `gen_random_uuid()`
    // is built into PostgreSQL 13+ core, so this works everywhere.
    @Modifying
    @Query(value = """
           INSERT INTO user_settings (id, user_id, key, enabled)
           VALUES (gen_random_uuid(), :userId, :key, false)
           ON CONFLICT (user_id, key) DO NOTHING
           """, nativeQuery = true)
    void upsertDefault(UUID userId, String key);

    @Modifying
    @Query("DELETE FROM UserSetting s WHERE s.userId = :userId")
    void deleteByUserId(UUID userId);
}
