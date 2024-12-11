package faang.school.postservice.model.event.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LikeKafkaEvent {
    private Long likeId;
    private Long postId;
}
