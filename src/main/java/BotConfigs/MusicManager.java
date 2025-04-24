package BotConfigs;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.http.client.config.RequestConfig;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class MusicManager {
    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final ScheduledExecutorService scheduler;
    private final Set<String> retryQueries = ConcurrentHashMap.newKeySet();

    public MusicManager() {
        this.playerManager = new DefaultAudioPlayerManager();
        this.musicManagers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);

        configurePlayerManager();
        startCleanupTask();
        

        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        YoutubeAudioSourceManager youtubeSourceManager = new YoutubeAudioSourceManager(true, null, null);
        youtubeSourceManager.setPlaylistPageCount(100);

        playerManager.registerSourceManager(youtubeSourceManager);
    }

    private void configurePlayerManager() {
        AudioConfiguration config = playerManager.getConfiguration();
        config.setFilterHotSwapEnabled(true);
        config.setOpusEncodingQuality(10);

        playerManager.setHttpBuilderConfigurator(builder -> {
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setSocketTimeout(10000)
                .setConnectionRequestTimeout(10000)
                .build();
            builder.setDefaultRequestConfig(requestConfig);
        });

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            Iterator<Map.Entry<Long, GuildMusicManager>> iterator = musicManagers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, GuildMusicManager> entry = iterator.next();
                try {
                    Guild guild = entry.getValue().getGuild();
                    if (guild == null || guild.getAudioManager().getConnectedChannel() == null) {
                        entry.getValue().destroy();
                        iterator.remove();
                    }
                } catch (Exception e) {
                    iterator.remove();
                }
            }
            retryQueries.clear();
        }, 1, 1, TimeUnit.HOURS);
    }

    public synchronized GuildMusicManager getGuildAudioPlayer(Guild guild, TextChannel textChannel) {
        long guildId = guild.getIdLong();
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager, guild, textChannel);
            musicManagers.put(guildId, musicManager);
        } else if (textChannel != null) {
            musicManager.setNotificationChannel(textChannel);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
        return musicManager;
    }

    public void loadAndPlay(Guild guild, String trackUrl, TextChannel textChannel, Consumer<String> callback) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, textChannel);
        String processedUrl = preprocessQuery(trackUrl);

        playerManager.loadItemOrdered(musicManager, processedUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(textChannel);
                if (musicManager.player.getPlayingTrack() == null) {
                    musicManager.scheduler.play(track);
                    callback.accept("🎵 Tocando agora: **" + track.getInfo().title + "**");
                } else {
                    musicManager.scheduler.queue(track);
                    callback.accept("📥 Adicionado à fila: **" + track.getInfo().title + "**");
                }
                retryQueries.remove(trackUrl.toLowerCase());
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    AudioTrack firstTrack = playlist.getTracks().get(0);
                    firstTrack.setUserData(textChannel);
                    if (musicManager.player.getPlayingTrack() == null) {
                        musicManager.scheduler.play(firstTrack);
                    } else {
                        musicManager.scheduler.queue(firstTrack);
                    }
                    callback.accept("🔍 Tocando: **" + firstTrack.getInfo().title + "**");
                } else {
                    AudioTrack firstTrack = playlist.getTracks().get(0);
                    firstTrack.setUserData(textChannel);
                    if (musicManager.player.getPlayingTrack() == null) {
                        musicManager.scheduler.play(firstTrack);
                    } else {
                        musicManager.scheduler.queue(firstTrack);
                    }
                    for (int i = 1; i < playlist.getTracks().size(); i++) {
                        AudioTrack track = playlist.getTracks().get(i);
                        track.setUserData(textChannel);
                        musicManager.scheduler.queue(track);
                    }
                    callback.accept("📚 Playlist adicionada: **" + playlist.getName() + "** (" + 
                                  playlist.getTracks().size() + " músicas)");
                }
                retryQueries.remove(trackUrl.toLowerCase());
            }

            @Override
            public void noMatches() {
                if (!retryQueries.contains(trackUrl.toLowerCase())) {
                    retryQueries.add(trackUrl.toLowerCase());
                    String fixedQuery = trackUrl.startsWith("ytsearch:") ? trackUrl : "ytsearch:" + trackUrl;
                    playerManager.loadItemOrdered(musicManager, fixedQuery, this);
                } else {
                    callback.accept("❌ Nenhum resultado encontrado para: " + trackUrl);
                    retryQueries.remove(trackUrl.toLowerCase());
                }
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if (!retryQueries.contains(trackUrl.toLowerCase())) {
                    retryQueries.add(trackUrl.toLowerCase());
                    String fixedQuery = trackUrl.startsWith("ytsearch:") ? trackUrl : "ytsearch:" + trackUrl;
                    playerManager.loadItemOrdered(musicManager, fixedQuery, this);
                } else {
                    callback.accept("❌ " + getFriendlyErrorMessage(exception, trackUrl));
                    retryQueries.remove(trackUrl.toLowerCase());
                }
            }
        });
    }

    private String preprocessQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Por favor, forneça um nome de música ou URL válida");
        }

        query = query.trim();

        if (query.contains("spotify.com") || query.startsWith("spotify:")) {
            throw new IllegalArgumentException("Links do Spotify ainda não são suportados.");
        }

        if (query.matches("^(https?)://.*")) {
            return query;
        }

        if (query.startsWith("ytsearch:")) {
            return query;
        }

        return query;
    }

    private String getFriendlyErrorMessage(FriendlyException exception, String query) {
        if (exception.getMessage().contains("status code for search response: 400")) {
            return "Problema temporário com o YouTube. Tente novamente mais tarde.";
        } else if (exception.getMessage().contains("status code for search response: 429")) {
            return "Muitas requisições. Por favor, espere um pouco antes de tentar novamente.";
        } else if (exception.getMessage().contains("No matches")) {
            return "Nenhum resultado encontrado para: " + query;
        }
        return "Falha ao carregar a mídia. Tente outro link ou termo de busca.";
    }

    public void skipTrack(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.scheduler.nextTrack();
    }

    public void pause(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.player.setPaused(true);
        if (musicManager.getNotificationChannel() != null) {
            musicManager.getNotificationChannel().sendMessage("⏸️ Música pausada").queue();
        }
    }

    public void resume(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.player.setPaused(false);
        if (musicManager.getNotificationChannel() != null) {
            musicManager.getNotificationChannel().sendMessage("▶️ Música retomada").queue();
        }
    }

    public void stop(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.scheduler.clearQueue();
        musicManager.player.stopTrack();
        if (musicManager.getNotificationChannel() != null) {
            musicManager.getNotificationChannel().sendMessage("⏹️ Parado e fila limpa").queue();
        }
    }

    public void setVolume(Guild guild, int volume) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        musicManager.player.setVolume(volume);
    }

    public String getNowPlaying(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        AudioTrack track = musicManager.player.getPlayingTrack();
        if (track == null) {
            return "Nenhuma música está tocando no momento";
        }
        return "🎶 Tocando agora: **" + track.getInfo().title + "**";
    }

    public String getQueueStatus(Guild guild) {
        GuildMusicManager musicManager = getGuildAudioPlayer(guild, null);
        return musicManager.scheduler.getQueueStatus();
    }

    public void shutdown() {
        scheduler.shutdown();
        musicManagers.values().forEach(manager -> {
            manager.player.destroy();
            if (manager.getGuild() != null) {
                manager.getGuild().getAudioManager().closeAudioConnection();
            }
        });
        musicManagers.clear();
        retryQueries.clear();
    }
}
