
package com.datarescue.chatbot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:4200")
public class ChatbotController {

    private final DatasetChatService datasetChatService;

    public ChatbotController(DatasetChatService datasetChatService) {
        this.datasetChatService = datasetChatService;
    }

    @PostMapping("/dataset/{id}")
    public ResponseEntity<ChatResponse> chatWithDataset(
            @PathVariable Long id,
            @Valid @RequestBody ChatRequest request) throws IOException {

        String answer = datasetChatService.askAboutDataset(
                id,
                request.message().trim()
        );

        return ResponseEntity.ok(new ChatResponse(answer));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException ex) {

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleServiceError(
            IllegalStateException ex) {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(ex.getMessage()));
    }

    public record ChatRequest(
            @NotBlank(message = "Please enter a question.")
            @Size(max = 1000, message = "Your question must be 1000 characters or fewer.")
            String message) {
    }

    public record ChatResponse(String answer) {
    }

    public record ErrorResponse(String error) {
    }
}

