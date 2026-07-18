package com.example.student.controller;

import com.example.student.model.Bookmark;
import com.example.student.model.User;
import com.example.student.service.BookmarkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @GetMapping
    public ResponseEntity<List<Bookmark>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookmarkService.list(user.getId()));
    }

    @PostMapping
    public ResponseEntity<?> add(@AuthenticationPrincipal User user, @Valid @RequestBody Map<String, String> body) {
        try {
            Bookmark saved = bookmarkService.add(
                    user.getId(),
                    body.get("type"),
                    body.get("refId"),
                    body.get("title"),
                    body.get("description"),
                    body.get("icon"));
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeById(@AuthenticationPrincipal User user, @PathVariable String id) {
        bookmarkService.removeById(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> removeByRef(@AuthenticationPrincipal User user,
                                            @RequestParam String type,
                                            @RequestParam String refId) {
        bookmarkService.removeByRef(user.getId(), type, refId);
        return ResponseEntity.noContent().build();
    }
}
