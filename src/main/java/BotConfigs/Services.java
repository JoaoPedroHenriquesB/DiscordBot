package BotConfigs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;

public class Services {
    private final MusicManager musicManager;

    public Services(MusicManager musicManager) {
        this.musicManager = musicManager;
    }
    
    public List<CommandData> getCommandList() {
        List<CommandData> commands = new ArrayList<>();
        
        commands.add(Commands.slash("play", "Toca uma música do YouTube/Spotify")
            .addOptions(new OptionData(OptionType.STRING, "query", "URL ou nome da música", true)));
        
        commands.add(Commands.slash("skip", "Pula a música atual"));
        commands.add(Commands.slash("pause", "Pausa a música atual"));
        commands.add(Commands.slash("resume", "Continua a música pausada"));
        commands.add(Commands.slash("stop", "Para a música e limpa a fila"));
        commands.add(Commands.slash("queue", "Mostra a fila de músicas"));
        commands.add(Commands.slash("nowplaying", "Mostra a música atual"));
        commands.add(Commands.slash("join", "Faz o bot entrar no seu canal de voz"));
        commands.add(Commands.slash("leave", "Faz o bot sair do canal de voz"));
        commands.add(Commands.slash("hello", "Diz olá para você"));
        commands.add(Commands.slash("littlecarl", "Mostra uma imagem do Carlinhos"));
        
        return commands;
    }

    public void executeCommand(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        
        try {
            switch(commandName) {
                case "play":
                    executePlay(event);
                    break;
                case "skip":
                    executeSkip(event);
                    break;
                case "pause":
                    executePause(event);
                    break;
                case "resume":
                    executeResume(event);
                    break;
                case "stop":
                    executeStop(event);
                    break;
                case "queue":
                    executeQueue(event);
                    break;
                case "nowplaying":
                    executeNowPlaying(event);
                    break;
                case "join":
                    executeJoin(event);
                    break;
                case "leave":
                    executeLeave(event);
                    break;
                case "hello":
                    executeHello(event);
                    break;
                case "littlecarl":
                    executeLittleCarl(event);
                    break;
                default:
                    event.reply("❌ Comando não reconhecido").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            handleCommandError(event, commandName, e);
        }
    }

    private void executePlay(SlashCommandInteractionEvent event) {
        String query = event.getOption("query").getAsString().trim();
        
        if (query.length() < 3) {
            event.reply("🔍 A busca deve ter pelo menos 3 caracteres").setEphemeral(true).queue();
            return;
        }
        
        if (!verifyVoiceChannel(event, false)) return;
        
        TextChannel textChannel = event.getChannel().asTextChannel();
        event.deferReply().queue(hook -> {
           
            String processedInput = enhanceSearchQuery(query);
            
            musicManager.loadAndPlay(event.getGuild(), processedInput, textChannel, message -> {
                if (message.startsWith("❌")) {
                    hook.editOriginal(message).queue();
                } else {
                    hook.editOriginal(message)
                        .setActionRow(
                            Button.primary("skip", "⏭️ Pular"),
                            Button.secondary("pause", "⏸️ Pausar"),
                            Button.success("resume", "▶️ Retomar"),
                            Button.danger("stop", "⏹️ Parar")
                        ).queue();
                }
            });
        });
    }

    private String enhanceSearchQuery(String query) {
        if (query.matches("^(https?|ftp|spotify)://.*$")) {
            return query;
        }
        return "ytsearch:" + query + " official audio"; 
    }

    private void executeSkip(SlashCommandInteractionEvent event) {
        if (!verifyVoiceChannel(event, true)) return;
        
        musicManager.skipTrack(event.getGuild());
        event.reply("⏭️ Pulando para a próxima música...").queue();
    }

    private void executePause(SlashCommandInteractionEvent event) {
        if (!verifyVoiceChannel(event, true)) return;
        
        musicManager.pause(event.getGuild());
        event.reply("⏸️ Música pausada").queue();
    }

    private void executeResume(SlashCommandInteractionEvent event) {
        if (!verifyVoiceChannel(event, true)) return;
        
        musicManager.resume(event.getGuild());
        event.reply("▶️ Música retomada").queue();
    }

    private void executeStop(SlashCommandInteractionEvent event) {
        if (!verifyVoiceChannel(event, true)) return;
        
        musicManager.stop(event.getGuild());
        event.reply("⏹️ Playback parado e fila limpa").queue();
    }

    private void executeQueue(SlashCommandInteractionEvent event) {
        String queueStatus = musicManager.getQueueStatus(event.getGuild());
        event.reply(queueStatus).setEphemeral(true).queue();
    }

    private void executeNowPlaying(SlashCommandInteractionEvent event) {
        String nowPlaying = musicManager.getNowPlaying(event.getGuild());
        event.reply(nowPlaying).setEphemeral(true).queue();
    }

    private void executeJoin(SlashCommandInteractionEvent event) {
        if (!event.getMember().getVoiceState().inAudioChannel()) {
            event.reply("❌ Você precisa estar em um canal de voz!").setEphemeral(true).queue();
            return;
        }

        AudioChannel voiceChannel = event.getMember().getVoiceState().getChannel();
        event.getGuild().getAudioManager().openAudioConnection(voiceChannel);
        event.reply("🔊 Conectado ao canal: " + voiceChannel.getAsMention()).queue();
    }

    private void executeLeave(SlashCommandInteractionEvent event) {
        if (!event.getGuild().getAudioManager().isConnected()) {
            event.reply("❌ Não estou em nenhum canal de voz!").setEphemeral(true).queue();
            return;
        }

        AudioChannel channel = event.getGuild().getAudioManager().getConnectedChannel();
        event.getGuild().getAudioManager().closeAudioConnection();
        event.reply("🚪 Saindo do canal: " + channel.getAsMention()).queue();
    }

    private void executeHello(SlashCommandInteractionEvent event) {
        String userName = event.getUser().getName();
        event.reply("👋 Olá, " + userName + "!").queue();
    }
    
    private void executeLittleCarl(SlashCommandInteractionEvent event) {
        try {
            File imageFile = new File("src/main/resources/carlinhos.webp");
            if (imageFile.exists()) {
                event.reply("**Aqui está o Carlinhos!**")
                     .addFiles(FileUpload.fromData(imageFile))
                     .queue();
            } else {
                event.reply("❌ A imagem não foi encontrada!")
                     .setEphemeral(true)
                     .queue();
            }
        } catch (Exception e) {
            event.reply("❌ Ocorreu um erro ao carregar a imagem: " + e.getMessage())
                 .setEphemeral(true)
                 .queue();
        }
    }

    private boolean verifyVoiceChannel(SlashCommandInteractionEvent event, boolean checkBotConnection) {
        if (!event.getMember().getVoiceState().inAudioChannel()) {
            event.reply("❌ Você precisa estar em um canal de voz para usar este comando!")
               .setEphemeral(true)
               .queue();
            return false;
        }
        
        if (checkBotConnection && !event.getGuild().getAudioManager().isConnected()) {
            event.reply("❌ Eu preciso estar conectado em um canal de voz primeiro!")
               .setEphemeral(true)
               .queue();
            return false;
        }
        
        return true;
    }

    private void handleCommandError(SlashCommandInteractionEvent event, String commandName, Exception e) {
        System.err.println("Erro ao executar comando " + commandName + ": " + e.getMessage());
        String errorMessage = "❌ Ocorreu um erro";
        
        if (e.getMessage().contains("No matches")) {
            errorMessage = "❌ Nenhum resultado encontrado";
        } else if (e.getMessage().contains("Loading failed")) {
            errorMessage = "❌ Falha ao carregar a mídia";
        }
        
        event.reply(errorMessage).setEphemeral(true).queue();
    }
}