package com.example.student.repository;

import com.example.student.model.UserDailyQuest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UserDailyQuestRepository extends MongoRepository<UserDailyQuest, String> {
    Optional<UserDailyQuest> findByUserIdAndQuestDate(String userId, LocalDate questDate);
    void deleteByUserId(String userId);
}
