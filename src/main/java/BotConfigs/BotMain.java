package BotConfigs;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class BotMain {
    public static void main(String[] args) {
        String TOKEN = System.getenv("DISCORD_BOT_TOKEN");
        if (TOKEN == null || TOKEN.isEmpty()) {
            System.err.println("ERRO: Token não encontrado");
            System.exit(1);
        }

        System.out.println("Iniciando bot...");
        
        try {
            MusicManager musicManager = new MusicManager();
            
            SlashCommandListener commandListener = new SlashCommandListener(musicManager);
            
            JDA jda = JDABuilder.createDefault(TOKEN)
                .setAutoReconnect(true)
                .setRequestTimeoutRetry(true)
                .enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.GUILD_VOICE_STATES,
                    GatewayIntent.GUILD_MEMBERS
                )
                .enableCache(CacheFlag.VOICE_STATE)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setActivity(Activity.listening("| /play"))
                .addEventListeners(commandListener)
                .build();

            jda.awaitReady();
            
            jda.addEventListener(new ButtonListener(musicManager));
            
            String botName = jda.getSelfUser().getGlobalName();
            if (botName == null) {
                botName = jda.getSelfUser().getName();
            }
            
            System.out.println("\nStatus final:");
            System.out.println("Bot: " + botName);
            System.out.println("Ping: " + jda.getGatewayPing() + "ms");
            System.out.println("Servidores: " + jda.getGuilds().size());
            
            System.out.println("\nListeners registrados:");
            jda.getRegisteredListeners().forEach(listener -> 
                System.out.println("- " + listener.getClass().getSimpleName())
            );
            
            jda.retrieveCommands().queue(
                commands -> {
                    System.out.println("\nComandos registrados: " + commands.size());
                    commands.forEach(cmd -> 
                        System.out.println("- " + cmd.getName() + ": " + cmd.getDescription())
                    );
                },
                error -> System.err.println("Falha ao verificar comandos: " + error.getMessage())
            );

        } catch (Exception e) {
            System.err.println("ERRO durante inicialização:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}