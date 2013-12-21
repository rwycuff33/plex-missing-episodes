package be.selckin.plex;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import us.nineworlds.plex.rest.PlexappFactory;
import us.nineworlds.plex.rest.model.impl.Media;
import us.nineworlds.plex.rest.model.impl.Part;
import us.nineworlds.plex.rest.model.impl.Video;

import java.util.regex.Pattern;

public class MovieDuplicates {

    public static final Pattern SEASON_PATTERN = Pattern.compile("Season ([0-9]+)");

    public static void main(String[] args) throws Exception {
        PlexappFactory plexapp = PlexUtils.newPlexappFactory("192.168.0.233", 32400);

        for (String key : PlexUtils.findMovieKeys(plexapp)) {
            for (Video video : plexapp.retrieveSections(key, "all").getVideos()) {

                ImmutableList<Part> parts = FluentIterable.from(video.getMedias()).transformAndConcat(new Function<Media, Iterable<Part>>() {
                    @Override
                    public Iterable<Part> apply(Media input) {
                        return input.getVideoPart();
                    }
                }).toList();

                if (parts.size() > 1) {
                    System.out.println(video.getTitle());
                    for (Part part : parts) {
                        System.out.println("\t" + part.getFilename());
                    }
                }
            }
        }
    }
}
