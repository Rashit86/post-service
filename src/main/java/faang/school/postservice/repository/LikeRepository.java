package faang.school.postservice.repository;

import faang.school.postservice.model.dto.LikeCount;
import faang.school.postservice.model.entity.Like;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LikeRepository extends CrudRepository<Like, Long> {

    void deleteByPostIdAndUserId(long postId, long userId);

    void deleteByCommentIdAndUserId(long commentId, long userId);

    List<Like> findByPostId(long postId);

    List<Like> findByCommentId(long commentId);

    Optional<Like> findByPostIdAndUserId(long postId, long userId);

    Optional<Like> findByCommentIdAndUserId(long commentId, long userId);

    @Query("SELECT COUNT(l) FROM Like l WHERE l.post.id = :postId")
    int countByPostId(long postId);

    @Query("SELECT new faang.school.postservice.model.dto.LikeCount(l.post.id, COUNT(l)) " +
            "FROM Like l " +
            "WHERE l.post.id IN :postIds " +
            "GROUP BY l.post.id")
    List<LikeCount> findLikeCountsByPostIds(@Param("postIds") List<Long> postIds);

}
