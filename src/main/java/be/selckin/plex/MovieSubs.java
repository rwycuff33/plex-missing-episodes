package be.selckin.plex;

import com.google.common.io.Files;
import us.nineworlds.plex.rest.PlexappFactory;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class MovieSubs {

    public static final Pattern SEASON_PATTERN = Pattern.compile("Season ([0-9]+)");

    public static void main(String[] args) throws Exception {
        PlexappFactory plexapp = PlexUtils.newPlexappFactory("192.168.0.233", 32400);

        try (DirectoryStream<Path> directoryStream = java.nio.file.Files.newDirectoryStream(Paths.get(args[0]))) {
            for (Path path : directoryStream) {
                if (isSub(path) && !hasMovie(path)) {

                    System.out.println(path.getFileName());
                }
            }
        }
    }

    private static boolean hasMovie(Path path) {
        String name = Files.getNameWithoutExtension(path.getFileName().toString());
        for (String ext : new String[]{"avi", "mkv", "mp4"}) {
            Path candidate = path.getParent().resolve(name + "." + ext);
            if (java.nio.file.Files.exists(candidate))
                return true;
        }
        return false;
    }

    private static boolean isSub(Path path) {
        String full = path.toString();
        return full.endsWith(".srt") || full.endsWith(".idx") || full.endsWith(".sub");
    }
}

