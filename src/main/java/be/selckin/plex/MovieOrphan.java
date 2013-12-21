package be.selckin.plex;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import us.nineworlds.plex.rest.PlexappFactory;
import us.nineworlds.plex.rest.model.impl.Media;
import us.nineworlds.plex.rest.model.impl.Part;
import us.nineworlds.plex.rest.model.impl.Video;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class MovieOrphan {

    public static final Pattern SEASON_PATTERN = Pattern.compile("Season ([0-9]+)");

    public static void main(String[] args) throws Exception {
        PlexappFactory plexapp = PlexUtils.newPlexappFactory("192.168.0.233", 32400);

        ImmutableSet<String> knownFiles = gatherRecognisedFiles(plexapp);

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(args[0]))) {
            for (Path path : directoryStream) {
                if (!isIgnored(path) && !knownFiles.contains(path.getFileName().toString())) {
                    System.out.println(path);
                }
            }
        }
    }

    private static boolean isIgnored(Path path) {
        String full = path.toString();
        return full.endsWith(".srt") || full.endsWith(".idx") || full.endsWith(".sub");
    }

    private static ImmutableSet<String> gatherRecognisedFiles(final PlexappFactory plexapp) throws Exception {
        return FluentIterable.from(PlexUtils.findMovieKeys(plexapp)).transformAndConcat(new Function<String, Iterable<Video>>() {
            @Override
            public Iterable<Video> apply(String input) {
                try {
                    return plexapp.retrieveSections(input, "all").getVideos();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }).transformAndConcat(new Function<Video, Iterable<Media>>() {
            @Override
            public Iterable<Media> apply(Video input) {
                return input.getMedias();
            }
        }).transformAndConcat(new Function<Media, Iterable<Part>>() {
            @Override
            public Iterable<Part> apply(Media input) {
                return input.getVideoPart();
            }
        }).transform(new Function<Part, String>() {
            @Override
            public String apply(Part input) {
                return Paths.get(input.getFilename()).getFileName().toString();
            }
        }).toSet();
    }
}
