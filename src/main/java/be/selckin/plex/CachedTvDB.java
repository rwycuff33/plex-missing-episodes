package be.selckin.plex;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.omertron.thetvdbapi.TheTVDBApi;
import com.omertron.thetvdbapi.model.Episode;
import com.omertron.thetvdbapi.model.Series;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.mapdb.DB;
import org.yamj.api.common.http.DefaultPoolingHttpClient;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.collect.FluentIterable.from;

public class CachedTvDB {

    private static final String LANGUAGE = "en";
    private static final Comparator<Episode> CMP_EPISODE_NR = new Comparator<Episode>() {
        @Override
        public int compare(Episode a, Episode b) {
            return Ints.compare(a.getEpisodeNumber(), b.getEpisodeNumber());
        }
    };
    private static final Predicate<Episode> AIRED_PAST = new Predicate<Episode>() {
        private final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.yearMonthDay();

        @Override
        public boolean apply(Episode input) {
            try {
                return dateTimeFormatter.parseDateTime(input.getFirstAired()).isBeforeNow();
            } catch (IllegalArgumentException ignored) {
                return true;
            }
        }
    };

    private final TheTVDBApi theTVDBApi;

    private final Map<String, List<Series>> seriesCache;
    private final Map<String, List<Episode>> episodeCache;

    public CachedTvDB(String apiKey, DB cache) {
        this.theTVDBApi = new TheTVDBApi(apiKey, new DefaultPoolingHttpClient());
        this.episodeCache = cache.createTreeMap("thetvdb-episodes").makeOrGet();
        this.seriesCache = cache.createTreeMap("thetvdb-series").makeOrGet();
    }

    public Optional<Show> find(final String title) {
        List<Series> result = cached(seriesCache, title, new Loader<List<Series>>() {
            @Override
            public List<Series> load() {
                return theTVDBApi.searchSeries(title, LANGUAGE);
            }
        });

        if (!result.isEmpty()) {
            Series series = result.get(0);
            return Optional.of(toShow(series));
        }
        return Optional.absent();
    }

    private Show toShow(Series series) {
        // crazy
        FluentIterable<Episode> episodes = from(getEpisodes(series)).filter(AIRED_PAST).filter(new Predicate<Episode>() {
            @Override
            public boolean apply(Episode input) {
                return input.getSeasonNumber() != 0;
            }
        });
        ImmutableList<Episode> list = episodes.toList();
        return new Show(series.getSeriesName(), from(episodes.index(new Function<Episode, Integer>() {
            @Override
            public Integer apply(Episode episode) {
                return episode.getSeasonNumber();
            }
        }).asMap().entrySet()).transform(new Function<Entry<Integer, Collection<Episode>>, Season>() {
            @Override
            public Season apply(Entry<Integer, Collection<Episode>> entry) {
                return new Season(entry.getKey(), Ordering.from(CMP_EPISODE_NR).immutableSortedCopy(entry.getValue()));
            }
        }).toList());
    }

    private List<Episode> getEpisodes(Series series) {
        final String id = series.getId();
        return cached(episodeCache, id, new Loader<List<Episode>>() {
            @Override
            public List<Episode> load() {
                return theTVDBApi.getAllEpisodes(id, LANGUAGE);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <K, V> V cached(Map<K, V> cache, K key, Loader<V> loader) {
        V cached = cache.get(key);
        if (cached == null) {
            cached = loader.load();
            cache.put(key, cached);
        }
        return cached;
    }

    public void close() {

    }

    public interface Loader<T> {
        T load();
    }


    public static class Show {
        private final String title;
        private final ImmutableList<Season> seasons;

        public Show(String title, ImmutableList<Season> seasons) {
            this.title = title;
            this.seasons = seasons;
        }

        public String getTitle() {
            return title;
        }

        public ImmutableList<Season> getSeasons() {
            return seasons;
        }
    }

    public static class Season {
        private final int season;
        private final List<Episode> episodes;

        public Season(int season, List<Episode> episodes) {
            this.season = season;
            this.episodes = episodes;
        }

        public int getSeason() {
            return season;
        }

        public List<Episode> getEpisodes() {
            return episodes;
        }
    }
}
