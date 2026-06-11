package com.aurunmusic.listeners;

import com.aurunmusic.AurunMusicPlugin;
import com.aurunmusic.catalog.MusicTrack;
import com.aurunmusic.managers.GuiManager;
import com.aurunmusic.managers.MusicManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Listener responsável por capturar cliques dentro do menu GUI do AurunMusic.
 *
 * ── SEGURANÇA DO INVENTÁRIO ───────────────────────────────────────────────────
 * O cancelamento do InventoryClickEvent é feito de forma SEGURA:
 * verificamos se o inventário clicado é o nosso menu antes de cancelar.
 * Isso evita que cliques em outros inventários (mochila, baús reais) sejam
 * cancelados indevidamente.
 *
 * ── IDENTIFICAÇÃO DO MENU ─────────────────────────────────────────────────────
 * Usamos o título traduzível "aurun.menu.title" para identificar o inventário.
 * Comparamos a chave de tradução do Component do título.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class GuiListener implements Listener {

    private final AurunMusicPlugin plugin;
    private final MusicManager musicManager;

    // Slots de músicas disponíveis no menu (sincronizado com GuiManager)
    private static final int[] MUSIC_SLOTS = GuiManager.getMusicSlots();

    public GuiListener(AurunMusicPlugin plugin, MusicManager musicManager) {
        this.plugin = plugin;
        this.musicManager = musicManager;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── EVENTO DE CLIQUE NO INVENTÁRIO ───────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intercepta cliques em inventários.
     *
     * Prioridade LOW para processar antes de outros plugins, mas ainda
     * permitindo que plugins de economia ou proteção cancelem primeiro se necessário.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {

        // ── Verifica se quem clicou é um jogador ──────────────────────────────
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ── Verifica se o inventário clicado é do tipo baú (CHEST) ───────────
        if (event.getInventory().getType() != InventoryType.CHEST) return;

        // ── Identifica se este inventário é o nosso menu ──────────────────────
        // Comparamos o título do inventário com a chave de tradução esperada.
        // Isso é necessário porque o título é um Component.translatable().
        Component inventoryTitle = event.getView().title();
        if (!isAurunMenu(inventoryTitle)) return;

        // ── A partir daqui: o inventário é o nosso menu ───────────────────────
        // Cancelamos o evento para evitar que o jogador mova itens do menu.
        event.setCancelled(true);

        // ── Ignora cliques fora do inventário superior (cliques no inventário do jogador) ─
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return;

        int slot = event.getSlot();

        // ── Tratamento por slot clicado ───────────────────────────────────────
        if (slot == GuiManager.SLOT_RANDOM) {
            handleRandomClick(player);
        } else if (slot == GuiManager.SLOT_STOP) {
            handleStopClick(player);
        } else {
            handleTrackSlotClick(player, slot);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── HANDLERS DE CLIQUE ────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Clique no botão de Modo Aleatório (slot 0).
     * Ativa o modo aleatório e exibe feedback traduzível.
     */
    private void handleRandomClick(Player player) {
        player.closeInventory();
        musicManager.startRandomMode(player);
        player.sendMessage(
                Component.translatable("aurun.cmd.random.start")
                        .color(NamedTextColor.AQUA)
        );
    }

    /**
     * Clique no botão Stop (slot 8).
     * Para tudo e permite que músicas nativas do Minecraft retomem.
     */
    private void handleStopClick(Player player) {
        player.closeInventory();
        musicManager.stopMusic(player);
        player.sendMessage(
                Component.translatable("aurun.cmd.stop.success")
                        .color(NamedTextColor.YELLOW)
        );
    }

    /**
     * Clique em um slot de faixa musical.
     *
     * Mapeia o slot clicado para o índice da faixa no catálogo {@link MusicTrack}.
     * Se o slot corresponder a uma faixa válida, reproduz imediatamente.
     *
     * @param player O jogador que clicou.
     * @param slot   O slot clicado no inventário.
     */
    private void handleTrackSlotClick(Player player, int slot) {
        // Busca o índice da faixa com base no slot clicado
        int trackIndex = getTrackIndexForSlot(slot);
        if (trackIndex < 0) return; // Slot sem função (painel de borda)

        MusicTrack[] tracks = MusicTrack.values();
        if (trackIndex >= tracks.length) return; // Slot além do catálogo disponível

        MusicTrack track = tracks[trackIndex];
        player.closeInventory();
        musicManager.playTrack(player, track);

        // Feedback com nome traduzível da faixa
        player.sendMessage(
                Component.translatable("aurun.cmd.track.playing",
                        Component.translatable(track.getTranslationKey())
                                .color(NamedTextColor.GREEN))
                        .color(NamedTextColor.YELLOW)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── UTILITÁRIOS ───────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifica se um Component de título corresponde ao menu do AurunMusic.
     *
     * Estratégia: converte o Component para string de examinação e verifica
     * se contém a chave de tradução esperada.
     * Isso é robusto mesmo com decorações de cor aplicadas ao translatable.
     *
     * @param title Component do título do inventário.
     * @return true se for o menu do AurunMusic.
     */
    private boolean isAurunMenu(Component title) {
        // Examina o Component para encontrar o translatable key
        // Usamos o plain text serializer para inspecionar o conteúdo
        String serialized = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText()
                .serialize(title);

        // Verifica se o título contém a tradução esperada ou a chave diretamente
        // (em ambientes onde a tradução não foi resolvida pelo servidor)
        return title.toString().contains("aurun.menu.title")
                || serialized.contains("aurun.menu.title");
    }

    /**
     * Mapeia um slot do inventário para o índice de faixa no array {@link MusicTrack#values()}.
     *
     * @param slot Slot clicado no inventário (0-indexed).
     * @return Índice da faixa, ou -1 se o slot não corresponder a nenhuma faixa.
     */
    private int getTrackIndexForSlot(int slot) {
        for (int i = 0; i < MUSIC_SLOTS.length; i++) {
            if (MUSIC_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
