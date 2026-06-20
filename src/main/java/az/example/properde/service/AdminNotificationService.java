package az.example.properde.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminNotificationService {
    private static final int MAX_ERROR_LENGTH = 500;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.notification.from-email:no-reply@perde.az}")
    private String fromEmail;

    @Value("${app.whatsapp.access-token:}")
    private String whatsappAccessToken;

    @Value("${app.whatsapp.phone-number-id:}")
    private String whatsappPhoneNumberId;

    @Value("${app.whatsapp.api-version:v20.0}")
    private String whatsappApiVersion;

    public DeliveryResult sendResetCode(String channel, String target, String code) {
        String text = "Perde.az admin reset kodu: " + code + ". Kod 15 dəqiqə keçərlidir.";
        if ("email".equalsIgnoreCase(channel)) {
            return sendEmail(target, "Perde.az admin reset kodu", text);
        }
        return sendWhatsapp(target, text);
    }

    private DeliveryResult sendEmail(String email, String subject, String text) {
        if (!StringUtils.hasText(email)) {
            return DeliveryResult.failed("email", email, "Email ünvanı boşdur");
        }
        if (!StringUtils.hasText(mailHost)) {
            return DeliveryResult.notConfigured("email", email, "SMTP konfiqurasiya edilməyib");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return DeliveryResult.notConfigured("email", email, "JavaMailSender aktiv deyil");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (StringUtils.hasText(fromEmail)) {
                message.setFrom(fromEmail);
            }
            message.setTo(email);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            return DeliveryResult.sent("email", email);
        } catch (Exception ex) {
            return DeliveryResult.failed("email", email, sanitizeError(ex));
        }
    }

    private DeliveryResult sendWhatsapp(String phone, String text) {
        if (!StringUtils.hasText(phone)) {
            return DeliveryResult.failed("phone", phone, "Telefon nömrəsi boşdur");
        }
        if (!StringUtils.hasText(whatsappAccessToken) || !StringUtils.hasText(whatsappPhoneNumberId)) {
            return DeliveryResult.notConfigured("phone", phone, "WhatsApp Cloud API konfiqurasiya edilməyib");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", phone.replaceAll("\\D", ""));
            payload.put("type", "text");
            payload.put("text", Map.of("preview_url", false, "body", text));

            String url = "https://graph.facebook.com/" + whatsappApiVersion + "/" + whatsappPhoneNumberId + "/messages";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + whatsappAccessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return DeliveryResult.sent("phone", phone);
            }
            return DeliveryResult.failed("phone", phone, truncate("WhatsApp API HTTP " + response.statusCode() + ": " + response.body()));
        } catch (Exception ex) {
            return DeliveryResult.failed("phone", phone, sanitizeError(ex));
        }
    }

    private String sanitizeError(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        return truncate(message);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    public record DeliveryResult(boolean delivered, String status, String channel, String target, String error) {
        public static DeliveryResult sent(String channel, String target) {
            return new DeliveryResult(true, "SENT", channel, target, null);
        }

        public static DeliveryResult notConfigured(String channel, String target, String error) {
            return new DeliveryResult(false, "NOT_CONFIGURED", channel, target, error);
        }

        public static DeliveryResult failed(String channel, String target, String error) {
            return new DeliveryResult(false, "FAILED", channel, target, error);
        }
    }
}
