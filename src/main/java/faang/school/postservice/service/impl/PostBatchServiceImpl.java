package faang.school.postservice.service.impl;

import faang.school.postservice.model.entity.Post;
import faang.school.postservice.model.event.application.PostsPublishCommittedEvent;
import faang.school.postservice.repository.PostRepository;
import faang.school.postservice.service.PostBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostBatchServiceImpl implements PostBatchService {
    private final PostRepository postRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePostBatch(List<Post> postBatch) {
        postRepository.saveAll(postBatch);
        applicationEventPublisher.publishEvent(new PostsPublishCommittedEvent(postBatch));
    }
}
