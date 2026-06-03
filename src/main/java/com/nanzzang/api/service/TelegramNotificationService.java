package com.nanzzang.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class TelegramNotificationService {

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.chat-id:}")
    private String chatId;

    private final RestTemplate restTemplate = new RestTemplate();

    public void send(String message) {
        if (botToken.isBlank() || chatId.isBlank()) return;

        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        try {
            restTemplate.postForObject(url, Map.of(
                "chat_id", chatId,
                "text", message,
                "parse_mode", "HTML"
            ), Map.class);
        } catch (Exception e) {
            log.warn("Telegram 알림 전송 실패: {}", e.getMessage());
        }
    }
}
