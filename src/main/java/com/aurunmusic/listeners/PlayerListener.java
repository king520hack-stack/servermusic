package com.aurunmusic.listeners;

import com.aurunmusic.managers.MusicManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener de eventos de ciclo de vida do jogador.
 *
 * ── ANTI-MEMORY-LEAK ──────────────────────────────────────────────────────────
 * Este listener é a peça CRÍTICA de prevenção de memory leak do plugin.
 *
 * Ao detectar a saída de um jogador (PlayerQuitEvent), chamamos IMEDIATAMENTE
 * {@link MusicManager#cleanupPlayer(UUID)} para:
 *  1. Cancelar a BukkitTask agendada para este UUID (evita execução de runnable
 *     após o jogador ter saído, o que causaria NullPointerException ou
 *     tentativa de acesso a entidade inválida).
 *  2. Remover o UUID de TODOS os HashMaps de estado (randomModeMap, scheduledTaskMap).
 *
 * Garantia: Após a execução do cleanupPlayer(), não existe NENHUMA referência
 * ao UUID deste jogador em memória no plugin. A heap é completamente liberada.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class PlayerListener implements Listener {

    private final MusicManager musicManager;

    public PlayerListener(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    /**
     * Limpa TODOS os rastros do jogador ao sair do servidor.
     *
     * Prioridade MONITOR: executamos depois de todos os outros handlers,
     * garantindo que o jogador já foi processado por todos os sistemas
     * antes de realizarmos a limpeza final.
     *
     * @param event Evento de saída do jogador.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Limpeza completa: cancela tasks e remove todas as entradas dos mapas
        musicManager.cleanupPlayer(uuid);
    }

    /**
     * Injeta o Resource Pack via GitHub Pages automaticamente quando o jogador entra.
     * Isso tira a carga de rede do servidor (CDN externa do GitHub Pages).
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();
        
        // Se o jogador for Bedrock (Geyser/Floodgate), abortamos o envio do Resource Pack via Java.
        // O Bedrock vai baixar o pacote nativo (.mcpack) diretamente na tela de login pelo Geyser.
        boolean isBedrock = false;
        try {
            // Tenta usar a API do Floodgate se estiver instalada
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                isBedrock = org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
            }
        } catch (NoClassDefFoundError | Exception e) {
            // Ignora se a API não estiver disponível
        }

        if (isBedrock) {
            com.aurunmusic.AurunMusicPlugin.getInstance().getLogger().info("[AurunMusic] Jogador " + player.getName() + " entrou usando BEDROCK. Ignorando envio do pacote via Java.");
            return; // Jogadores Bedrock ignoram este passo
        } else {
            com.aurunmusic.AurunMusicPlugin.getInstance().getLogger().info("[AurunMusic] Jogador " + player.getName() + " entrou usando JAVA. Preparando para enviar pacote de texturas em 1 segundo...");
        }

        
        // URL direta do GitHub Pages
        String packUrl = "https://king520hack-stack.github.io/servermusic/aurun_music.zip";
        
        // SHA-1 OBRIGATORIAMENTE EM LETRAS MINÚSCULAS! (Minecraft Client rejeita maiúsculas)
        String packHash = "16e597befcaf0b6cbf15cd6b10a55371ff9c30f7";
        
        // Prompt customizado avisando sobre a necessidade do pack
        net.kyori.adventure.text.Component prompt = net.kyori.adventure.text.Component.text(
                "Baixe o Resource Pack oficial de músicas para escutar o AurunMusic!"
        ).color(net.kyori.adventure.text.format.NamedTextColor.GOLD);

        // Atraso de 20 ticks (1 segundo) para garantir que o cliente já renderizou o mundo
        // e consegue receber o pacote do Resource Pack sem ignorar
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    com.aurunmusic.AurunMusicPlugin.getInstance().getLogger().info("[AurunMusic] Disparando pacote URL (" + packUrl + ") para " + player.getName());
                    player.setResourcePack(packUrl, packHash, false, prompt);
                }
            }
        }.runTaskLater(com.aurunmusic.AurunMusicPlugin.getInstance(), 20L);
    }
}
