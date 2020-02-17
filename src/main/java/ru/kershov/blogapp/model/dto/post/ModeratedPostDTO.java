package ru.kershov.blogapp.model.dto.post;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.Getter;
import org.jsoup.Jsoup;
import ru.kershov.blogapp.config.Config;
import ru.kershov.blogapp.model.Post;
import ru.kershov.blogapp.model.User;
import ru.kershov.blogapp.utils.JsonViews;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ModeratedPostDTO {
    @Getter
    @JsonView(JsonViews.IdName.class)
    private int id;

    @Getter
    @JsonView(JsonViews.IdName.class)
    private String time;

    @Getter
    @JsonView(JsonViews.IdName.class)
    private User user;

    @Getter
    @JsonView(JsonViews.IdName.class)
    private String title;

    @Getter
    @JsonView(JsonViews.IdName.class)
    private String announce;

    public ModeratedPostDTO(int id, Post post) {
        this.id = id;

        this.time = DateTimeFormatter.ofPattern(Config.STRING_MODERATED_POST_DATE_FORMAT)
                .withZone(ZoneId.systemDefault())
                .format(post.getTime());

        this.user = post.getAuthor();
        this.title = post.getTitle();
        this.announce = Jsoup.parse(post.getText()).text();
    }
}
