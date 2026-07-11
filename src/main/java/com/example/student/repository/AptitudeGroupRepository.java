package com.example.student.repository;

import com.example.student.model.AptitudeGroup;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AptitudeGroupRepository extends MongoRepository<AptitudeGroup, String> {

    /** Active groups in one category, in display order. */
    List<AptitudeGroup> findByCategoryAndIsActiveTrueOrderByOrderAsc(String category);

    Optional<AptitudeGroup> findBySlug(String slug);

    /** All groups (incl. inactive) for the admin panel, grouped by category then order. */
    List<AptitudeGroup> findAllByOrderByCategoryAscOrderAsc();

    boolean existsBySlug(String slug);
}
