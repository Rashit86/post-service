package faang.school.postservice.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.postservice.model.event.kafka.AuthorPostByHeatKafkaEvent;
import faang.school.postservice.model.event.kafka.AuthorPostKafkaEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthorPostByHeatKafkaProducer extends AbstractKafkaProducer<AuthorPostByHeatKafkaEvent> {

    @Value("${kafka.topics.author-post-by-heat}")
    private String authorPostByHeatKafkaTopic;

    public AuthorPostByHeatKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        super(kafkaTemplate, objectMapper);
    }

    @Override
    protected String getTopic() {
        return authorPostByHeatKafkaTopic;
    }
}

