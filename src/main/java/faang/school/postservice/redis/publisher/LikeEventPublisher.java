package faang.school.postservice.redis.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import faang.school.postservice.model.enums.LikePostEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Component
public class LikeEventPublisher extends AbstractEventPublisher<LikePostEvent> {

    public LikeEventPublisher(@Qualifier("eventRedisTemplate") RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper,
                              @Qualifier("likeEventTopic") ChannelTopic channelTopic) {
        super(redisTemplate, objectMapper, channelTopic);
    }
}
