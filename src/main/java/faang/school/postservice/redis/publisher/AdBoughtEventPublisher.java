package faang.school.postservice.redis.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.postservice.model.event.AdBoughtEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Component
public class AdBoughtEventPublisher extends AbstractEventPublisher<AdBoughtEvent> {
    public AdBoughtEventPublisher(@Qualifier("eventRedisTemplate") RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper,
                                  @Qualifier("adBoughtTopic") ChannelTopic channelTopic) {
        super(redisTemplate, objectMapper, channelTopic);
    }
}
