package faang.school.postservice.service.impl;

import faang.school.postservice.client.ProjectServiceClient;
import faang.school.postservice.client.UserServiceClient;
import faang.school.postservice.config.context.UserContext;
import faang.school.postservice.mapper.PostMapper;
import faang.school.postservice.model.dto.PostDto;
import faang.school.postservice.model.dto.ProjectDto;
import faang.school.postservice.model.dto.UserDto;
import faang.school.postservice.model.entity.Post;
import faang.school.postservice.model.enums.AuthorType;
import faang.school.postservice.model.event.application.PostViewCommittedEvent;
import faang.school.postservice.model.event.application.PostsPublishCommittedEvent;
import faang.school.postservice.redis.publisher.NewPostPublisher;
import faang.school.postservice.repository.PostRepository;
import faang.school.postservice.service.BatchProcessService;
import faang.school.postservice.service.PostBatchService;
import faang.school.postservice.service.PostService;
import faang.school.postservice.util.moderation.ModerationDictionary;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    @Value("${spell-checker.batch-size}")
    private int correcterBatchSize;

    @Value("${post.publisher.batch-size}")
    private int batchSize;

    @Value("${post.moderation.batch-size}")
    private int moderationBatchSize;

    private final PostRepository postRepository;
    private final UserServiceClient userServiceClient;
    private final ProjectServiceClient projectServiceClient;
    private final PostMapper postMapper;
    private final NewPostPublisher newPostPublisher;
    private final ModerationDictionary moderationDictionary;
    private final BatchProcessService batchProcessService;
    private final ExecutorService schedulingThreadPoolExecutor;
    private final PostBatchService postBatchService;
    private final UserContext userContext;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public PostDto createPost(PostDto postDto) {
        if (postDto.getAuthorType() == AuthorType.USER) {
            UserDto user = userServiceClient.getUser(postDto.getAuthorId());
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
        } else if (postDto.getAuthorType() == AuthorType.PROJECT) {
            ProjectDto project = projectServiceClient.getProject(postDto.getAuthorId());
            if (project == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
            }
        } else {
            throw new IllegalArgumentException("Invalid author type");
        }

        Post post = postMapper.toPost(postDto);
        Post savedPost = postRepository.save(post);
        PostDto result = postMapper.toPostDto(savedPost);

        newPostPublisher.publish(result);
        return result;
    }

    @Override
    @Transactional
    public PostDto publishPost(Long id) {
        Post post = getPostById(id);

        if (post.isPublished()) {
            throw new IllegalStateException("Post is already published");
        }

        post.setPublished(true);
        post.setPublishedAt(LocalDateTime.now());
        log.debug("Saving post with id = {} in DB", post.getId());
        Post savedPost = postRepository.save(post);
        PostDto postDto = postMapper.toPostDto(savedPost);
        applicationEventPublisher.publishEvent(new PostsPublishCommittedEvent(List.of(savedPost)));
        return postDto;
    }

    @Override
    public PostDto updatePost(Long id, PostDto postDto) {
        Post post = getPostById(id);

        if (!post.getAuthorId().equals(postDto.getAuthorId()) || !postDto.getAuthorType().equals(postDto.getAuthorType())) {
            throw new IllegalStateException("Cannot change author or author type of the post");
        }

        post.setContent(postDto.getContent());
        postRepository.save(post);

        return postMapper.toPostDto(post);
    }

    @Override
    public void deletePost(Long id) {
        Post post = getPostById(id);
        post.setDeleted(true);
        postRepository.save(post);
    }

    @Transactional
    @Override
    public PostDto getPost(Long id) {
        Post post = incrementViewCountAndGetPost(id);
        applicationEventPublisher.publishEvent(new PostViewCommittedEvent(id, post.getAuthorId(), userContext.getUserId()));
        return postMapper.toPostDto(post);
    }

    @Override
    public List<PostDto> getUserDrafts(Long authorId) {
        return postRepository.findByAuthorId(authorId).stream()
                .filter(post -> !post.isDeleted() && !post.isPublished())
                .map(postMapper::toPostDto)
                .sorted(Comparator.comparing(PostDto::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<PostDto> getProjectDrafts(Long projectId) {
        return postRepository.findByProjectId(projectId).stream()
                .filter(post -> !post.isDeleted() && !post.isPublished())
                .map(postMapper::toPostDto)
                .sorted(Comparator.comparing(PostDto::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public List<PostDto> getAllPostPublishedByUser(Long authorId) {
        List<PostDto> dtos = postRepository.findByAuthorId(authorId).stream()
                .filter(post -> !post.isDeleted() && post.isPublished())
                .map(postMapper::toPostDto)
                .sorted(Comparator.comparing(PostDto::getPublishedAt).reversed())
                .collect(Collectors.toList());

        if (!dtos.isEmpty()) {
            dtos.forEach(postDto -> {
                        incrementPostViewCount(postDto.getId());
                        applicationEventPublisher.publishEvent(
                                new PostViewCommittedEvent(postDto.getId(), authorId, userContext.getUserId()));
                    }
            );
        }
        return dtos;
    }

    @Transactional
    @Override
    public List<PostDto> getAllPostPublishedByProject(Long projectId) {
        List<PostDto> dtos = postRepository.findByProjectId(projectId).stream()
                .filter(post -> !post.isDeleted() && post.isPublished())
                .map(postMapper::toPostDto)
                .sorted(Comparator.comparing(PostDto::getPublishedAt).reversed())
                .collect(Collectors.toList());

        if (!dtos.isEmpty()) {
            dtos.forEach(postDto -> {
                        incrementPostViewCount(postDto.getId());
                        applicationEventPublisher.publishEvent(
                                new PostViewCommittedEvent(postDto.getId(), postDto.getAuthorId(), userContext.getUserId()));
                    }
            );
        }
        return dtos;
    }

    private Post getPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostDto> getAllPostsByHashtagId(String content, Pageable pageable) {
        Page<PostDto> pagesDtos = postRepository.findByHashtagsContent(content, pageable).map(postMapper::toPostDto);
        if (pagesDtos.getSize() > 0) {
            pagesDtos.getContent().forEach(postDto -> {
                        incrementPostViewCount(postDto.getId());
                        applicationEventPublisher.publishEvent(
                                new PostViewCommittedEvent(postDto.getId(), postDto.getAuthorId(), userContext.getUserId()));
                    }
            );
        }
        return pagesDtos;
    }

    @Override
    @Transactional(readOnly = true)
    public Post getPostByIdInternal(Long id) {
        Post post = incrementViewCountAndGetPost(id);
        applicationEventPublisher.publishEvent(
                new PostViewCommittedEvent(id, post.getAuthorId(), userContext.getUserId()));
        return post;
    }

    @Override
    @Transactional
    public Post updatePostInternal(Post post) {
        return postRepository.save(post);
    }

    @Override
    public List<CompletableFuture<Void>> publishScheduledPosts() {
        List<Post> readyToPublish = postRepository.findReadyToPublish();
        log.info("{} posts were found for scheduled publishing", readyToPublish.size());
        List<List<Post>> postBatches = partitionList(readyToPublish, batchSize);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<Post> postBatch : postBatches) {
            postBatch.forEach(post -> {
                post.setPublished(true);
                post.setPublishedAt(LocalDateTime.now());
                log.info("Post with id '{}' prepared for scheduled publishing", post.getId());
            });
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> postBatchService.savePostBatch(postBatch), schedulingThreadPoolExecutor);
            futures.add(future);
        }
        return futures;
    }

    private List<List<Post>> partitionList(List<Post> list, int batchSize) {
        List<List<Post>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }


    @Transactional
    public void correctSpellingInUnpublishedPosts() {
        List<Post> unpublishedPosts = postRepository.findReadyForSpellCheck();

        if (!unpublishedPosts.isEmpty()) {
            int batchSize = correcterBatchSize;
            List<List<Post>> batches = partitionList(unpublishedPosts, batchSize);

            List<CompletableFuture<Void>> futures = batches.stream()
                    .map(batchProcessService::processBatch)
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    @Override
    public List<List<Post>> findAndSplitUnverifiedPosts() {
        List<Post> unverifiedPosts = postRepository.findAllByVerifiedDateIsNull();

        return partitionList(unverifiedPosts, moderationBatchSize);
    }

    @Override
    @Async("postOperationsAsyncExecutor")
    @Transactional
    public CompletableFuture<Void> verifyPostsForSwearWords(List<Post> unverifiedPostsBatch) {
        return CompletableFuture.runAsync(() -> {
            unverifiedPostsBatch.forEach(post -> {
                boolean hasImproperContent = moderationDictionary.containsSwearWords(post.getContent());
                post.setVerified(!hasImproperContent);
                post.setVerifiedDate(LocalDateTime.now());
            });

            postRepository.saveAll(unverifiedPostsBatch);
        });
    }

    private void incrementPostViewCount(long postId) {
        try {
            int updatedRows = postRepository.incrementViewCount(postId);
            if (updatedRows == 0) {
                throw new EntityNotFoundException("Post not found with id " + postId);
            }
        } catch (OptimisticLockException e) {
            throw new IllegalStateException("Failed to increment view count due to concurrent modification", e);
        }
    }

    @Override
    public int getViewCount(Long postId) {
        return postRepository.getViewCountByPostId(postId);
    }

    private Post incrementViewCountAndGetPost(long postId) {
        Post post = getPostById(postId);
        try {
            int incremented = postRepository.incrementViewCount(postId);
            if (incremented == 0) {
                throw new EntityNotFoundException("Post not found with id " + postId);
            }
            return post;
        } catch (OptimisticLockException e) {
            throw new IllegalStateException("Failed to increment view count due to concurrent modification", e);
        }
    }
}
