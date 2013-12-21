package be.selckin.plex;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import us.nineworlds.plex.rest.PlexappFactory;
import us.nineworlds.plex.rest.config.impl.Configuration;
import us.nineworlds.plex.rest.model.impl.Directory;
import us.nineworlds.plex.rest.model.impl.MediaContainer;

import java.util.List;

public class PlexUtils {
    private PlexUtils() {
    }

    public static PlexappFactory newPlexappFactory(String host, int port) {
        Configuration config = new Configuration();
        config.setHost(host);
        config.setPort(Integer.toString(port));
        return PlexappFactory.getInstance(config);
    }

    public static Iterable<Directory> filter(MediaContainer mediaContainer, final String type) throws Exception {
        return Iterables.filter(mediaContainer.getDirectories(), new Predicate<Directory>() {
            @Override
            public boolean apply(Directory directory) {
                return type.equals(directory.getType());
            }
        });
    }

    public static List<String> findShowKeys(PlexappFactory plexapp) throws Exception {
        return findSectionKeys(plexapp, "show");
    }

    public static List<String> findMovieKeys(PlexappFactory plexapp) throws Exception {
        return findSectionKeys(plexapp, "movie");
    }

    private static List<String> findSectionKeys(PlexappFactory plexapp, String type) throws Exception {
        return FluentIterable.from(filter(plexapp.retrieveSections(), type)).transform(new Function<Directory, String>() {
            @Override
            public String apply(Directory directory) {
                return directory.getKey();
            }
        }).toList();

    }
}
