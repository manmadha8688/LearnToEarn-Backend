package com.example.student.repository;

import com.example.student.model.UserDailyQuest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface UserDailyQuestRepository extends MongoRepository<UserDailyQuest, String> {
    // Returns a list (not Optional) so a legacy duplicate (userId, questDate) row can never
    // throw IncorrectResultSizeDataAccessException on read. The unique index normally keeps
    // this to 0 or 1; DataIntegrityMigration dedupes any historical duplicates on startup.
    List<UserDailyQuest> findByUserIdAndQuestDate(String userId, LocalDate questDate);
    void deleteByUserId(String userId);
}
