package com.ai.spring_lens.service;

import com.ai.spring_lens.model.response.ChatResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SpringAiChatService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatService.class);
    private static final String SYSTEM_PROMPT ="""
                        You are SpringLens, a helpful AI assistant.
                        Answer questions based on the provided context.
                        Always cite the source document and page number.
                        If the answer is not in the context, say so clearly.
                        """;
    private final ChatClient chatClient;
    private final CircuitBreaker circuitBreaker;
    private final VectorStore vectorStore;

    public SpringAiChatService(ChatClient.Builder builder,
                               CircuitBreakerRegistry circuitBreakerRegistry,
                               VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.circuitBreaker = circuitBreakerRegistry
                .circuitBreaker("groqClient");
    }

    public Mono<ChatResponse> chat(String message) {
        long start = System.currentTimeMillis();

        return Mono.fromCallable(() -> {
                    // Step 1: retrieve relevant chunks from PGVector
                    List<Document> relevantDocs = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(message)
                                    .topK(4)
                                    .similarityThreshold(0.7)
                                    .build()
                    );

                    // Step 2: build context from retrieved chunks
                    String context = relevantDocs.stream()
                            .map(doc -> "Source: " + doc.getMetadata().get("file_name") +
                                    " (Page " + doc.getMetadata().get("page_number") + ")" +
                                    System.lineSeparator() + "Content: " +
                                    doc.getFormattedContent())
                            .collect(Collectors.joining(
                                    System.lineSeparator() + System.lineSeparator() +
                                            "---" + System.lineSeparator() + System.lineSeparator()
                            ));

                    // Step 3: build augmented prompt
                    // Step 3: build augmented prompt
                    String augmentedMessage = relevantDocs.isEmpty()
                            ? "The user asked: " + message +
                            "\n\nNo relevant information was found in the knowledge base. " +
                            "Politely inform the user that this question is outside the " +
                            "scope of the available documents."
                            : "Context from documents:" + System.lineSeparator() +
                            context + System.lineSeparator() + System.lineSeparator() +
                            "Question: " + message;

                    log.info("RAG retrieved {} chunks for query={}",
                            relevantDocs.size(), message);

                    // Step 4: call LLM with augmented prompt
                    return chatClient.prompt()
                            .user(augmentedMessage)
                            .call()
                            .chatResponse();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .map(response -> new ChatResponse(
                        response.getResult().getOutput().getText(),
                        response.getMetadata().getModel(),
                        response.getMetadata().getUsage().getPromptTokens(),
                        response.getMetadata().getUsage().getCompletionTokens(),
                        response.getMetadata().getUsage().getTotalTokens(),
                        System.currentTimeMillis() - start
                ))
                .onErrorResume(ex -> {
                    log.warn("Fallback triggered message={} reason={}",
                            message, ex.getMessage());
                    return Mono.just(new ChatResponse(
                            "Service temporarily unavailable. Please try again shortly.",
                            "fallback", 0, 0, 0, 0L
                    ));
                });
    }
}