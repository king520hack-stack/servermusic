package com.aurunmusic.managers;

import com.aurunmusic.AurunMusicPlugin;
import com.aurunmusic.catalog.MusicTrack;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador central de estado de música para cada jogador.
 *
 * Responsabilidades:
 * - Armazenar o estado do Modo Aleatório por UUID (NUNCA por instância de Player).
 * - Rastrear a BukkitTask agendada para cada jogador no modo aleatório.
 * - Executar o fluxo completo de parada e início de músicas.
 * - Realizar limpeza total de memória no quit e no shutdown do servidor.
 *
 * ── ANTI-MEMORY-LEAK ──────────────────────────────────────────────────────────
 * Todos os mapas usam UUID como chave. Instâncias de Player NUNCA são armazenadas.
 * O método cleanupPlayer() é chamado obrigatoriamente no PlayerQuitEvent.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class MusicManager {

    private final AurunMusicPlugin plugin;

    /**
     * Mapa de UUID -> boolean indicando se o Modo Aleatório está ativo.
     * ConcurrentHashMap para segurança se algum evento acontecer em thread paralela.
     */
    private final Map<UUID, Boolean> randomModeMap = new ConcurrentHashMap<>();

    /**
     * Mapa de UUID -> BukkitTask da próxima troca de música no Modo Aleatório.
     * Guardamos a task para poder cancelá-la quando o jogador sair ou parar manualmente.
     */
    private final Map<UUID, BukkitTask> scheduledTaskMap = new ConcurrentHashMap<>();

    // Instância Random para seleção de faixas aleatórias
    private final Random random = new Random();

    public MusicManager(AurunMusicPlugin plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── MÉTODOS PÚBLICOS DE CONTROLE ─────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Para todas as músicas do catálogo para o jogador e desativa o Modo Aleatório.
     * Limpa o canal RECORDS (músicas do catálogo) e também silencia o canal MUSIC
     * (músicas nativas do Minecraft) — a partir daqui o jogo retoma seu ciclo normal.
     *
     * Chamado por: /armusic stop, botão Stop da GUI.
     *
     * @param player O jogador a ter a música parada.
     */
    public void stopMusic(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getLogger().info("[AurunMusic] Parando todas as musicas para o jogador " + player.getName());

        // ── 1. Desativa o Modo Aleatório ──────────────────────────────────────
        randomModeMap.put(uuid, false);

        // ── 2. Cancela a task agendada (próxima troca de música) ──────────────
        cancelScheduledTask(uuid);

        // ── 3. Limpa os canais de áudio no cliente ────────────────────────────
        clearAudioChannels(player);
    }

    /**
     * Para a música atual e inicia uma faixa específica imediatamente.
     *
     * Fluxo:
     * 1. Desativa modo aleatório e cancela tasks pendentes.
     * 2. Limpa os canais de áudio (evita sobreposição).
     * 3. Reproduz a faixa via Adventure Sound API (processamento 100% no cliente).
     *
     * @param player O jogador alvo.
     * @param track  A faixa a ser reproduzida.
     */
    public void playTrack(Player player, MusicTrack track) {
        UUID uuid = player.getUniqueId();

        // ── 1. Cancela qualquer modo aleatório ou faixa anterior ──────────────
        randomModeMap.put(uuid, false);
        cancelScheduledTask(uuid);

        // ── 2. Limpa os canais ANTES de reproduzir (sem sobreposição) ─────────
        clearAudioChannels(player);

        // ── 3. Dispara o som via Adventure API → processado 100% pelo cliente ─
        dispatchSound(player, track);
    }

    /**
     * Ativa o Modo Aleatório para o jogador.
     *
     * Fluxo:
     * 1. Marca o UUID como random=true.
     * 2. Cancela tasks anteriores.
     * 3. Limpa canais de áudio.
     * 4. Escolhe e reproduz uma faixa aleatória.
     * 5. Agenda a próxima troca baseada na duração da faixa atual.
     *
     * @param player O jogador alvo.
     */
    public void startRandomMode(Player player) {
        UUID uuid = player.getUniqueId();

        // ── 1. Ativa o modo no mapa de estado ────────────────────────────────
        randomModeMap.put(uuid, true);

        // ── 2. Cancela tasks pendentes de ciclos anteriores ───────────────────
        cancelScheduledTask(uuid);

        // ── 3. Limpa os canais e inicia o ciclo ───────────────────────────────
        clearAudioChannels(player);

        // ── 4. Seleciona e reproduz faixa inicial ─────────────────────────────
        MusicTrack firstTrack = pickRandomTrack();
        dispatchSound(player, firstTrack);

        // ── 5. Agenda a próxima troca de música ───────────────────────────────
        scheduleNextTrack(uuid, firstTrack.getDurationTicks());
    }

    /**
     * Verifica se o Modo Aleatório está ativo para o UUID fornecido.
     *
     * @param uuid UUID do jogador.
     * @return true se o Modo Aleatório estiver ativo.
     */
    public boolean isRandomMode(UUID uuid) {
        return randomModeMap.getOrDefault(uuid, false);
    }

    /**
     * Realiza limpeza COMPLETA de memória para um jogador ao sair do servidor.
     *
     * Chamado EXCLUSIVAMENTE pelo PlayerQuitEvent.
     * Cancela tasks, remove todas as entradas dos mapas por UUID.
     * Garante que NENHUM rastro do jogador permaneça na heap.
     *
     * @param uuid UUID do jogador que saiu.
     */
    public void cleanupPlayer(UUID uuid) {
        // Cancela a task agendada imediatamente para evitar execução pós-quit
        cancelScheduledTask(uuid);

        // Remove o estado de Modo Aleatório do mapa
        randomModeMap.remove(uuid);

        // Remove a entrada da task (já cancelada acima)
        scheduledTaskMap.remove(uuid);
    }

    /**
     * Cancela TODAS as tasks e limpa TODOS os mapas.
     * Chamado no onDisable() do plugin para garantir shutdown limpo.
     */
    public void shutdownAll() {
        // Cancela cada task individualmente para liberação imediata
        scheduledTaskMap.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });

        // Limpa todos os mapas de estado
        scheduledTaskMap.clear();
        randomModeMap.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── MÉTODOS INTERNOS ──────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Limpa os canais de áudio do cliente.
     *
     * - SoundStop.named("minecraft:music.*") → para músicas ambiente nativas do Minecraft.
     * - SoundStop para SoundSource.RECORD → para qualquer música anterior do catálogo.
     *
     * IMPORTANTE: Ambos os stops são necessários para garantir que nenhum áudio
     * seja reproduzido sobreposto ao novo som que será iniciado em seguida.
     */
    private void clearAudioChannels(Player player) {
        // Para o canal de música ambiente nativa do Minecraft (biome music, etc.)
        player.stopSound(SoundStop.named(Key.key("minecraft", "music.game")));
        player.stopSound(SoundStop.named(Key.key("minecraft", "music.creative")));
        player.stopSound(SoundStop.named(Key.key("minecraft", "music.end")));
        player.stopSound(SoundStop.named(Key.key("minecraft", "music.nether")));
        player.stopSound(SoundStop.named(Key.key("minecraft", "music.under_water")));
        player.stopSound(SoundStop.named(Key.key("minecraft", "music.menu")));

        // Para o canal MASTER e RECORDS
        player.stopSound(SoundStop.source(net.kyori.adventure.sound.Sound.Source.RECORD));
        player.stopSound(SoundStop.source(net.kyori.adventure.sound.Sound.Source.MASTER));

        // Para qualquer som aurun que possa estar tocando
        for (MusicTrack track : MusicTrack.values()) {
            String[] parts = track.getSoundKey().split(":", 2);
            if (parts.length == 2) {
                player.stopSound(SoundStop.named(Key.key(parts[0], parts[1])));
            }
        }
    }

    /**
     * Despacha um som via Adventure Sound API.
     *
     * O som é reproduzido na categoria RECORD para que o cliente possa:
     * - Controlar o volume independentemente (slider de Records no menu de som).
     * - Processar o .ogg 100% localmente (ZERO carga no servidor).
     *
     * Volume 1.0f = volume máximo.
     * Pitch  1.0f = velocidade original da faixa.
     *
     * @param player O jogador que receberá o som.
     * @param track  A faixa a ser reproduzida.
     */
    private void dispatchSound(Player player, MusicTrack track) {
        String[] parts = track.getSoundKey().split(":", 2);
        Key soundKey = (parts.length == 2)
                ? Key.key(parts[0], parts[1])
                : Key.key(track.getSoundKey());

        Sound sound = Sound.sound(
                soundKey,
                Sound.Source.MASTER,  // Categoria MASTER → Garantia que vai tocar se o som do jogo estiver ligado
                1000f,                // Volume alto para garantir que ouçam
                1.0f                  // Pitch normal (velocidade original)
        );

        plugin.getLogger().info("[AurunMusic] Disparando pacote de som '" + soundKey.asString() + "' para " + player.getName() + " na categoria MASTER (Volume 1000)");
        player.playSound(sound);
    }

    /**
     * Agenda a próxima troca de música no Modo Aleatório.
     *
     * O BukkitRunnable é agendado para rodar após {@code delayTicks} ticks.
     * Ao rodar, ele verifica (BLINDAGEM):
     *  - Se o jogador ainda está online (evita NullPointerException e acesso inválido).
     *  - Se o Modo Aleatório ainda está ativo para este UUID.
     *
     * Somente se ambas as condições forem verdadeiras, uma nova faixa é disparada
     * e uma nova task é agendada. Caso contrário, o ciclo termina sem re-execução.
     *
     * @param uuid       UUID do jogador (NUNCA Player — anti-leak).
     * @param delayTicks Ticks a aguardar antes de trocar para a próxima faixa.
     */
    private void scheduleNextTrack(UUID uuid, long delayTicks) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // ── BLINDAGEM 1: Verifica se o jogador ainda está online ───────
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    // Jogador saiu: limpeza já foi feita no PlayerQuitEvent.
                    // Apenas terminamos este ciclo sem reagendar.
                    scheduledTaskMap.remove(uuid);
                    return;
                }

                // ── BLINDAGEM 2: Verifica se o Modo Aleatório ainda está ativo ─
                if (!randomModeMap.getOrDefault(uuid, false)) {
                    // O jogador desativou o modo aleatório manualmente.
                    // Encerra o ciclo sem tocar nova música.
                    scheduledTaskMap.remove(uuid);
                    return;
                }

                // ── Tudo ok: seleciona e reproduz a próxima faixa ─────────────
                MusicTrack nextTrack = pickRandomTrack();

                // Limpa canais antes de reproduzir a próxima (evita sobreposição)
                clearAudioChannels(player);
                dispatchSound(player, nextTrack);

                // Agenda o próximo ciclo baseado na duração desta nova faixa
                scheduleNextTrack(uuid, nextTrack.getDurationTicks());
            }
        }.runTaskLater(plugin, delayTicks);

        // Armazena a nova task no mapa (sobrescreve qualquer task anterior)
        scheduledTaskMap.put(uuid, task);
    }

    /**
     * Cancela a BukkitTask agendada para o UUID informado, se existir.
     * Operação segura: verifica se a task não foi já cancelada antes de chamar cancel().
     *
     * @param uuid UUID do jogador.
     */
    private void cancelScheduledTask(UUID uuid) {
        BukkitTask existing = scheduledTaskMap.remove(uuid);
        if (existing != null && !existing.isCancelled()) {
            existing.cancel();
        }
    }

    /**
     * Seleciona uma faixa aleatória do catálogo {@link MusicTrack}.
     *
     * @return Uma MusicTrack escolhida aleatoriamente.
     */
    private MusicTrack pickRandomTrack() {
        MusicTrack[] tracks = MusicTrack.values();
        return tracks[random.nextInt(tracks.length)];
    }
}
