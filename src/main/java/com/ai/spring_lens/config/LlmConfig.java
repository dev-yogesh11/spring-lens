package com.ai.spring_lens.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LlmConfig {

    // ── API Beans ─────────────────────────────────────────────────────────────

    @Bean("groqApi")
    public OpenAiApi groqApi(LlmProperties props) {
        return OpenAiApi.builder()
                .baseUrl(props.getGroq().getBaseUrl())
                .apiKey(props.getGroq().getApiKey())
                .build();
    }

    @Bean("openAiApi")
    public OpenAiApi openAiApi(LlmProperties props) {
        return OpenAiApi.builder()
                .baseUrl(props.getOpenai().getBaseUrl())
                .apiKey(props.getOpenai().getApiKey())
                .build();
    }

    @Bean("ollamaApi")
    public OpenAiApi ollamaApi(LlmProperties props) {
        return OpenAiApi.builder()
                .baseUrl(props.getOllama().getBaseUrl())
                .apiKey("ollama") // dummy key required by builder
                .build();
    }

    // ── ChatModel Beans ───────────────────────────────────────────────────────

    @Bean("groqChatModel")
    @Primary
    public OpenAiChatModel groqChatModel(
            @Qualifier("groqApi") OpenAiApi api,
            LlmProperties props
    ) {
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(props.getGroq().getModel())
                        .build())
                .build();
    }

    @Bean("openAiChatModel")
    public OpenAiChatModel openAiChatModel(
            @Qualifier("openAiApi") OpenAiApi api,
            LlmProperties props
    ) {
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(props.getOpenai().getModel())
                        .build())
                .build();
    }

    @Bean("ollamaChatModel")
    public OpenAiChatModel ollamaChatModel(
            @Qualifier("ollamaApi") OpenAiApi api,
            LlmProperties props
    ) {
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(props.getOllama().getModel())
                        .build())
                .build();
    }

    // ── ChatClient Beans ──────────────────────────────────────────────────────

    @Bean("groqChatClient")
    public ChatClient groqChatClient(
            @Qualifier("groqChatModel") OpenAiChatModel model
    ) {
        return ChatClient.create(model);
    }

    @Bean("openAiChatClient")
    public ChatClient openAiChatClient(
            @Qualifier("openAiChatModel") OpenAiChatModel model
    ) {
        return ChatClient.create(model);
    }

    @Bean("ollamaChatClient")
    public ChatClient ollamaChatClient(
            @Qualifier("ollamaChatModel") OpenAiChatModel model
    ) {
        return ChatClient.create(model);
    }
}