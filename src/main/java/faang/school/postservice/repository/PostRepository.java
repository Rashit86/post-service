package faang.school.postservice.repository;

import faang.school.postservice.model.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByAuthorId(long authorId);

    List<Post> findByProjectId(long projectId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.likes WHERE p.projectId = :projectId")
    List<Post> findByProjectIdWithLikes(long projectId);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.likes WHERE p.authorId = :authorId")
    List<Post> findByAuthorIdWithLikes(long authorId);

    @Query("SELECT p FROM Post p WHERE p.published = false AND p.deleted = false AND p.scheduledAt <= CURRENT_TIMESTAMP")
    List<Post> findReadyToPublish();

    @Query("SELECT p FROM Post p WHERE p.published = false" +
            " AND p.deleted = false" +
            " AND p.scheduledAt < CURRENT_TIMESTAMP" +
            " AND p.spellCheckCompleted = false")
    List<Post> findReadyForSpellCheck();

    @Query(value = "SELECT p FROM Post p JOIN p.hashtags h WHERE h.id = :hashtagId", nativeQuery = true)
    List<Post> findByHashtagId(Long hashtagId);

    Page<Post> findByHashtagsContent(String content, Pageable pageable);

    @Query(nativeQuery = true, value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM post_album WHERE album_id = :albumId AND post_id = :postId")
    boolean existsInAlbum(long albumId, long postId);

    List<Post> findAllByVerifiedDateIsNull();

    @Query("SELECT p.authorId " +
            "FROM Post p " +
            "WHERE p.verified = false AND p.deleted = false " +
            "AND p.authorId BETWEEN :minAuthorId AND :maxAuthorId " +
            "GROUP BY p.authorId " +
            "HAVING COUNT(p) > 5")
    List<Long> findAuthorsWithMoreThanFiveUnverifiedPostsInRange(Long minAuthorId, Long maxAuthorId);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId")
    int incrementViewCount(@Param("postId") long postId);

    @Query("SELECT p.viewCount FROM Post p WHERE p.id = :postId")
    int getViewCountByPostId(@Param("postId") Long postId);

    @Query("SELECT p FROM Post p " +
            "WHERE p.published = true AND p.deleted = false " +
            "AND p.publishedAt BETWEEN :fromDate AND :toDate " +
            "ORDER BY p.publishedAt DESC")
    Page<Post> findPostsByDateRange(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}
