package com.example.student.repository;

import com.example.student.model.CodingProblem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CodingProblemRepository extends MongoRepository<CodingProblem, String> {
    List<CodingProblem> findAllByOrderByOrderIndexAsc();
}
