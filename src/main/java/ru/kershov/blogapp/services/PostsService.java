package ru.kershov.blogapp.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.kershov.blogapp.config.Config;
import ru.kershov.blogapp.enums.ModerationDecision;
import ru.kershov.blogapp.enums.ModerationStatus;
import ru.kershov.blogapp.enums.MyPostsModerationStatus;
import ru.kershov.blogapp.enums.PostMode;
import ru.kershov.blogapp.model.Comment;
import ru.kershov.blogapp.model.Post;
import ru.kershov.blogapp.model.Tag;
import ru.kershov.blogapp.model.User;
import ru.kershov.blogapp.model.dto.PostListDTO;
import ru.kershov.blogapp.model.dto.post.ModeratedPostDTO;
import ru.kershov.blogapp.model.dto.post.NewPostDTO;
import ru.kershov.blogapp.model.dto.post.PostDTO;
import ru.kershov.blogapp.repositories.CommentsRepository;
import ru.kershov.blogapp.repositories.PostsRepository;
import ru.kershov.blogapp.repositories.TagsRepository;
import ru.kershov.blogapp.repositories.VotesRepository;
import ru.kershov.blogapp.utils.APIResponse;
import ru.kershov.blogapp.utils.DateUtils;
import ru.kershov.blogapp.utils.OffsetBasedPageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class PostsService {
    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private VotesRepository votesRepository;

    @Autowired
    private TagsRepository tagsRepository;

    @Autowired
    private TagsService tagsService;

    @Autowired
    private CommentsRepository commentsRepository;

    @Autowired
    private UserAuthService userAuthService;

    public ResponseEntity<?> getPosts(int offset, int limit, String postMode) {
        final Instant now = Instant.now();
        final PostMode mode;

        Sort sort = Sort.by(Sort.Direction.DESC, "time");

        try {
            mode = PostMode.getByName(postMode);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(APIResponse.error(e.getMessage()));
        }

        switch (mode) {
            /* сортировать по дате публикации, выводить сначала старые */
            case EARLY:
                sort = Sort.by(Sort.Direction.ASC, "time");
                break;

            /* сортировать по убыванию количества лайков */
            case BEST:
                sort = Sort.by(Sort.Direction.DESC, "like_count");
                break;

            /* сортировать по убыванию количества комментариев */
            case POPULAR:
                /* сортировать по дате публикации, выводить сначала новые */
            case RECENT:
            default:
                break;
        }

        /* TODO:
         *      Maybe replace with custom Pageable (utils.OffsetBasedPageable) implementation
         *      Current implementation works only in terms of page numbers, i.e.
         *      if request is offset=0&limit=10 and the total number of elements is 14
         *      we'll get 2 pages: 0th page of 10 elements and 1st page of 4 elements.
         *      If request is offset=5&limit=10, we'll still get 2 pages 0 and 1
         *      with 10 and 4 elements respectively. Though it's OK in terms of assignment,
         *      this solution is not as optimal as implementing our own custom pageable,
         *      where we can offset in terms of elements and not pages.
         */
        int page = (offset + limit) / limit - 1;
        Pageable pageable = PageRequest.of(page, limit, sort);
        Page<PostDTO> posts = postsRepository.findAllPosts(now, pageable);

        if (mode == PostMode.POPULAR) {
            final List<PostDTO> p = new ArrayList<>(posts.getContent());
            Collections.sort(p);
            posts = new PageImpl<>(p);
        }

        return ResponseEntity.ok(new PostListDTO(posts));
    }

    public ResponseEntity<?> searchPosts(int offset, int limit, String query) {
        if (query == null || query.length() < Config.INT_POST_MIN_QUERY_LENGTH)
            return ResponseEntity.badRequest().body(APIResponse.error(Config.STRING_POST_INVALID_QUERY));

        Sort sort = Sort.by(Sort.Direction.DESC, "time");
        Pageable pageable = PageRequest.of(offset, limit, sort);
        Page<PostDTO> posts = postsRepository.findAllPostsByQuery(Instant.now(), query, pageable);

        return ResponseEntity.ok(new PostListDTO(posts));
    }

    public ResponseEntity<?> getPost(int id) {
        /* TODO: IMPORTANT!!!
         *       If we get only `isActive = true AND moderationStatus = 'NEW'`
         *       we won't be able to edit some posts at the backend anymore.
         *       If we need this endpoint working properly, we have to add a couple of
         *       new endpoints for getting posts for authors & moderators, i.e.:
         *       /api/post/my/{id} and /api/post/moderation/{id}.
         *       It's also important as frontend and backed have different requirements on
         *       time format (for backend we need yyyy-MM-ddTHH:MM while frontend uses
         *       a bunch of other formats). Suggestion: add service field to
         *       `{ ... "service_time" : yyyy-MM-ddTHH:MM ... }` and refactor frontend app to use this
         *       service field to fulfill publication date field while editing posts.
         */
        Optional<Post> postOptional = postsRepository.findById(id);

        if (postOptional.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    APIResponse.error(String.format(Config.STRING_POST_NOT_FOUND, id))
            );

        Post post = postOptional.get();

        PostDTO postDTO = new PostDTO(post);

        postDTO.setTime(DateUtils.formatDate(post.getTime(), Config.STRING_NEW_POST_DATE_FORMAT));
        postDTO.setLikeCount(votesRepository.findByPostAndValue(post, (byte) 1).size());
        postDTO.setDislikeCount(votesRepository.findByPostAndValue(post, (byte) -1).size());
        postDTO.setTags(tagsRepository.findTagNamesByPost(post));

        final List<Comment> comments = commentsRepository.findByPost(post);
        postDTO.setComments(comments);

        // Update view count for requested post
        post.updateViewCount();
        Post savedPost = postsRepository.save(post);

        if (post.getId() != savedPost.getId())
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        return ResponseEntity.ok(postDTO);
    }

    public ResponseEntity<?> searchByDate(int offset, int limit, String date) {
        if (!DateUtils.isValidDate(date))
            return ResponseEntity.badRequest().body(APIResponse.error(Config.STRING_POST_INVALID_DATE));

        Sort sort = Sort.by(Sort.Direction.DESC, "time");
        Pageable pageable = PageRequest.of(offset, limit, sort);
        Page<PostDTO> posts = postsRepository.findAllPostsByDate(Instant.now(), date, pageable);

        return ResponseEntity.ok(new PostListDTO(posts));
    }

    public ResponseEntity<?> searchByTag(int offset, int limit, String tagName) {
        Tag tag = tagsRepository.findByNameIgnoreCase(tagName);

        if (tag == null)
            return ResponseEntity.badRequest().body(
                    APIResponse.error(String.format(Config.STRING_POST_INVALID_TAG, tagName))
            );

        Sort sort = Sort.by(Sort.Direction.DESC, "time");
        Pageable pageable = PageRequest.of(offset, limit, sort);

        Page<PostDTO> posts = postsRepository.findAllPostsByTag(Instant.now(), tag, pageable);

        return ResponseEntity.ok(new PostListDTO(posts));
    }

    public Post savePost(Post post, NewPostDTO postData, User editor) {
        final Post postToSave = (post == null) ? new Post() : post;
        final Instant NOW = Instant.now();

        postToSave.setTitle(postData.getTitle());
        postToSave.setText(postData.getText());
        postToSave.setActive(postData.getActive());
        postToSave.setTime(postData.getTime().isBefore(NOW) ? NOW : postData.getTime());
        postToSave.setAuthor((postToSave.getId() == 0) ? editor : postToSave.getAuthor());

        if ((post == null) || (editor.equals(postToSave.getAuthor()) && !editor.isModerator())) {
            postToSave.setModerationStatus(ModerationStatus.NEW);
        }

        if (postData.getTags() != null) {
            postData.getTags().forEach(tag -> postToSave.getTags().add(tagsService.saveTag(tag)));
        }

        return postsRepository.save(postToSave);
    }

    public ResponseEntity<?> getModeratedPosts(int offset, int limit, User user, ModerationStatus status) {
        final Sort sort = Sort.by(Sort.Direction.DESC, "time");
        final Pageable pageable = new OffsetBasedPageRequest(offset, limit, sort);
        final User moderator = (status.equals(ModerationStatus.NEW)) ? null : user;

        final Page<ModeratedPostDTO> posts = postsRepository.findModeratedPosts(moderator, status, pageable);

        return ResponseEntity.ok(new PostListDTO(posts));
    }

    public ResponseEntity<?> updatePostModerationStatus(User moderator, Post post, ModerationDecision decision) {
        final User postModerator = post.getModeratedBy();

        if (postModerator != null && !postModerator.equals(moderator)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    APIResponse.error(Config.STRING_MODERATION_INVALID_POST)
            );
        }

        // Ok as `decision` is prevalidated by @ValidModerationDecision
        ModerationStatus status = (decision == ModerationDecision.ACCEPT)
                ? ModerationStatus.ACCEPTED
                : ModerationStatus.DECLINED;

        post.setModerationStatus(status);
        post.setModeratedBy(moderator);

        Post savedPost = postsRepository.save(post);

        return ResponseEntity.ok(APIResponse.ok("id", savedPost.getId()));
    }

    public ResponseEntity<?> getMyPosts(int offset, int limit, User user, MyPostsModerationStatus status) {
        final Sort sort = Sort.by(Sort.Direction.DESC, "time");
        final Pageable pageable = new OffsetBasedPageRequest(offset, limit, sort);
        final boolean isActive = status.isActive();
        final ModerationStatus postStatus = status.getModerationStatus();

        final Page<PostDTO> posts = postsRepository.findMyPosts(user, isActive, postStatus, pageable);

        return ResponseEntity.ok(new PostListDTO(posts));
    }
}
