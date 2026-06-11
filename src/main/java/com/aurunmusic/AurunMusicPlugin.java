package com.aurunmusic;

import com.aurunmusic.commands.ArMusicCommand;
import com.aurunmusic.listeners.GuiListener;
import com.aurunmusic.listeners.PlayerListener;
import com.aurunmusic.managers.MusicManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Classe principal do plugin AurunMusic.
 *
 * Ponto de entrada do plugin, responsável por:
 * - Registrar todos os comandos e listeners.
 * - Instanciar o MusicManager (gerenciador de estado central).
 * - Realizar shutdown limpo liberando toda memória ao desligar.
 */
public final class AurunMusicPlugin extends JavaPlugin {

    // Instância singleton segura para uso via getPlugin()
    private static AurunMusicPlugin instance;

    // Gerenciador central de músicas e estado dos jogadores
    private MusicManager musicManager;

    @Override
    public void onEnable() {
        instance = this;

        // ── Instancia o gerenciador central ──────────────────────────────────
        this.musicManager = new MusicManager(this);

        // ── Registra o comando /armusic com seu executor e TabCompleter ───────
        ArMusicCommand arMusicCommand = new ArMusicCommand(this, musicManager);
        var cmd = getCommand("armusic");
        if (cmd != null) {
            cmd.setExecutor(arMusicCommand);
            cmd.setTabCompleter(arMusicCommand);
        }

        // ── Registra os listeners ─────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new GuiListener(this, musicManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(musicManager), this);

        getLogger().info("AurunMusic v" + getDescription().getVersion() + " ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        // Cancela TODAS as tasks agendadas pelo plugin para evitar leaks pós-shutdown
        if (musicManager != null) {
            musicManager.shutdownAll();
        }
        getLogger().info("AurunMusic desativado. Todas as tasks canceladas.");
    }

    /**
     * Retorna a instância singleton do plugin.
     * Útil para BukkitRunnables que precisam referenciar o plugin.
     */
    public static AurunMusicPlugin getInstance() {
        return instance;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }
}
