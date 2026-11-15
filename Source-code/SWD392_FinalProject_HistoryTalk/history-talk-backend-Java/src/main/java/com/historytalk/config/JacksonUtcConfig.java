package com.historytalk.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Ensures all LocalDateTime fields are serialized as ISO-8601 UTC with a trailing 'Z'
 * and deserialized cleanly whether the client sends a 'Z'/offset or a bare LocalDateTime.
 */
@Configuration
public class JacksonUtcConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override
                public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                        throws IOException {
                    if (value == null) {
                        gen.writeNull();
                        return;
                    }
                    gen.writeString(DateTimeFormatter.ISO_INSTANT.format(value.atZone(ZoneOffset.UTC).toInstant()));
                }
            });

            builder.deserializerByType(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                @Override
                public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    String raw = p.getValueAsString();
                    if (raw == null || raw.isBlank()) {
                        return null;
                    }
                    String text = raw.trim();
                    try {
                        return OffsetDateTime.parse(text)
                                .withOffsetSameInstant(ZoneOffset.UTC)
                                .toLocalDateTime();
                    } catch (DateTimeParseException ignored) {
                        return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                }
            });
        };
    }
}
