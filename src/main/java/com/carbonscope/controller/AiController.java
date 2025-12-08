package com.carbonscope.controller;

import com.carbonscope.dto.ChatRequest;
import com.carbonscope.service.GeminiService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/stream")
    public Flux<String> streamAi(@RequestBody ChatRequest request) {
        return geminiService.streamResponse(request);
    }

}