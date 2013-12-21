package be.selckin.plex;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import us.nineworlds.plex.rest.PlexappFactory;
import us.nineworlds.plex.rest.model.impl.Media;
import us.nineworlds.plex.rest.model.impl.Part;
import us.nineworlds.plex.rest.model.impl.Video;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class MovieRename {

    public static final Pattern SEASON_PATTERN = Pattern.compile("Season ([0-9]+)");

    public static void main(String[] args) throws Exception {
        PlexappFactory plexapp = PlexUtils.newPlexappFactory("192.168.0.233", 32400);

        Scripter script = new Scripter();

        for (String key : PlexUtils.findMovieKeys(plexapp)) {
            for (Video video : plexapp.retrieveSections(key, "all").getVideos()) {

                for (Media media : video.getMedias()) {
                    script.next();
                    List<Part> parts = media.getVideoPart();
                    if (parts.size() == 1) {
                        script.rename(video, parts.iterator().next(), Optional.<Integer>absent());
                    } else {
                        for (int i = 0; i < parts.size(); i++) {
                            script.rename(video, parts.get(i), Optional.of(i + 1));
                        }
                    }
                }

            }
        }

        Files.write(script.toString(), new File("rename.sh"), StandardCharsets.UTF_8);
    }

    public static class Scripter {

        private final StringBuilder script = new StringBuilder(256);

        private final Set<String> newNames = Sets.newHashSet();

        public void rename(Video video, Part part, Optional<Integer> disk) {
            String oldName = Paths.get(part.getFilename()).getFileName().toString();
            String newName = name(video, part, disk);

            script.append("mv ");
            quote(oldName);
            script.append(' ');
            quote(newName);
            script.append('\n');

            if (!newNames.add(newName))
                throw new RuntimeException("Duplicate " + newName);
        }

        private void quote(String value) {
            script.append('"').append(CharMatcher.is('"').replaceFrom(value, "\\\"")).append('"');
        }

        private String name(Video video, Part part, Optional<Integer> disk) {
            StringBuilder sb = new StringBuilder(128);
            sb.append(cleanUpTitle(video));
            if (!Strings.isNullOrEmpty(video.getYear()))
                sb.append('.').append(video.getYear());
            if (disk.isPresent())
                sb.append(".pt").append(disk.get());
            sb.append('.').append(Files.getFileExtension(part.getFilename()));
            return sb.toString();
        }

        public void next() {
            script.append('\n');
        }

        private String cleanUpTitle(Video video) {
            return trim(removeSpaces(removeSpecial(amperToAnd(video.getTitle()))));
        }

        private String amperToAnd(String title) {
            return CharMatcher.is('&').replaceFrom(title, " and ");
        }

        private String trim(String title) {
            return CharMatcher.is('.').trimAndCollapseFrom(title, '.');
        }

        private String removeSpaces(String title) {
            return CharMatcher.anyOf(" ,").replaceFrom(title, ".");
        }

        private String removeSpecial(String title) {
            return CharMatcher.anyOf(":!'").removeFrom(title);
        }

        @Override
        public String toString() {
            return script.toString();
        }
    }
}

