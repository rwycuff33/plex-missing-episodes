package be.selckin.plex;

import be.selckin.plex.CachedTvDB.Season;
import be.selckin.plex.CachedTvDB.Show;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.omertron.thetvdbapi.model.Episode;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import us.nineworlds.plex.rest.PlexappFactory;
import us.nineworlds.plex.rest.config.impl.Configuration;
import us.nineworlds.plex.rest.model.impl.Directory;
import us.nineworlds.plex.rest.model.impl.MediaContainer;
import us.nineworlds.plex.rest.model.impl.Video;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MissingEps {

    public static final Pattern SEASON_PATTERN = Pattern.compile("Season ([0-9]+)");

    public static void main(String[] args) throws Exception {
        PlexappFactory plexapp = newPlexappFactory("192.168.0.233", 32400);
        DB cache = newCache("pme.cache");
        CachedTvDB tvMeta = new CachedTvDB(args[0], cache);

        try {
            findMissing(plexapp, tvMeta);
        } finally {
            tvMeta.close();
            cache.commit();
            cache.close();
        }
    }

    private static void findMissing(PlexappFactory plexapp, CachedTvDB tvMeta) throws Exception {
        for (String key : findShowKeys(plexapp)) {
            for (Directory showDir : plexapp.retrieveSections(key, "all").getDirectories()) {

                Optional<Show> showOptional = tvMeta.find(showDir.getTitle());
                if (!showOptional.isPresent()) {
                    System.out.println();
                    System.out.println(":-( " + showDir.getTitle());
                    System.out.println("\t --- Could not find reference db");
                } else {
                    Show expected = showOptional.get();
                    Multimap<Integer, Integer> missing = findMissing(collectEpisodes(plexapp, showDir), expected);
                    System.out.println();
                    if (!missing.isEmpty()) {
                        System.out.println(":-( " + showDir.getTitle());
                        System.out.println("\tCompared with '" + expected.getTitle() + "'");
                        for (Entry<Integer, Collection<Integer>> entry : missing.asMap().entrySet()) {
                            System.out.println("\tSeason " + entry.getKey() + ": " + Ordering.natural().sortedCopy(entry.getValue()));
                        }
                    } else {
                        System.out.println(":-) " + showDir.getTitle());
                    }
                }
            }
        }
    }

    private static Multimap<Integer, Integer> collectEpisodes(PlexappFactory plexapp, Directory showDir) throws Exception {
        Multimap<Integer, Integer> seasonEpisodes = HashMultimap.create();
        for (Directory seasonDir : filter(plexapp.retrieveEpisodes(showDir.getKey()), "season")) {
            Matcher matcher = SEASON_PATTERN.matcher(seasonDir.getTitle());
            if (matcher.find()) {
                int season = Integer.parseInt(matcher.group(1));

                for (Video video : plexapp.retrieveEpisodes(seasonDir.getKey()).getVideos()) {
                    seasonEpisodes.put(season, Integer.parseInt(video.getEpisode()));
                }
            }
        }
        return seasonEpisodes;
    }

    private static Multimap<Integer, Integer> findMissing(Multimap<Integer, Integer> seasonEpisodes, Show expected) {
        Multimap<Integer, Integer> missing = HashMultimap.create();
        for (Season season : expected.getSeasons()) {
            for (Episode episode : season.getEpisodes()) {
                int seasonNr = season.getSeason();
                int episodeNr = episode.getEpisodeNumber();

                if (!seasonEpisodes.containsEntry(seasonNr, episodeNr))
                    missing.put(seasonNr, episodeNr);
            }
        }
        return missing;
    }

    private static DB newCache(String path) {
        return DBMaker.newFileDB(new File(path))
                .closeOnJvmShutdown()
                .transactionDisable()
                .make();
    }


    private static PlexappFactory newPlexappFactory(String host, int port) {
        Configuration config = new Configuration();
        config.setHost(host);
        config.setPort(Integer.toString(port));
        return PlexappFactory.getInstance(config);
    }

    private static Iterable<Directory> filter(MediaContainer mediaContainer, final String type) throws Exception {
        return Iterables.filter(mediaContainer.getDirectories(), new Predicate<Directory>() {
            @Override
            public boolean apply(Directory directory) {
                return type.equals(directory.getType());
            }
        });
    }

    private static List<String> findShowKeys(PlexappFactory plexapp) throws Exception {
        return FluentIterable.from(filter(plexapp.retrieveSections(), "show")).transform(new Function<Directory, String>() {
            @Override
            public String apply(Directory directory) {
                return directory.getKey();
            }
        }).toList();

    }


}
