package com.sportytask.api.controller;

import com.sportytask.domain.entities.Event;
import com.sportytask.infrastructure.producer.EventMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventPublisherController {

    private final EventMessageProducer eventMessageProducer;

    @PostMapping("/publish")
    public ResponseEntity<String> publishEvent(@RequestBody Event event) {
        log.info("Received request to publish event: {}", event);
        eventMessageProducer.sendEvent(event);
        return ResponseEntity.ok("Event published successfully");
    }
}
