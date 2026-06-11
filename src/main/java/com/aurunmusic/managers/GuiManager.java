package com.aurunmusic.managers;

import com.aurunmusic.catalog.MusicTrack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Gerenciador e construtor da GUI (Interface Gráfica) do menu do AurunMusic.
 *
 * Responsabilidades:
 * - Construir o inventário Chest (27 slots) com todos os itens clicáveis.
 * - Usar Component.translatable() para TODOS os textos visíveis (nome, lore).
 *   Isso garante tradução nativa no cliente (Java e Bedrock via Geyser),
 *   sem detecção de idioma no servidor.
 * - Definir o título do inventário como translatable (chave: aurun.menu.title).
 * - Expor constantes de slot para uso no GuiListener (evento de clique).
 */
public class GuiManager {

    // ── Constantes de slot do inventário (0-indexed, inventário de 27 slots) ──

    /** Slot do botão de Modo Aleatório (jukebox — canto superior esquerdo). */
    public static final int SLOT_RANDOM = 0;

    /** Slot do botão de Stop (barreira — canto superior direito). */
    public static final int SLOT_STOP = 8;

    /** Slots dos painéis de borda (slots sem função, apenas decorativos). */
    private static final int[] BORDER_SLOTS = {1, 2, 3, 4, 5, 6, 7, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};

    /**
     * Constrói e retorna o inventário do menu principal do AurunMusic.
     *
     * O título usa Component.translatable() com a chave "aurun.menu.title",
     * que deve estar definida nos arquivos de idioma do Resource Pack.
     *
     * @return Inventário populado e pronto para ser aberto ao jogador.
     */
    public Inventory buildMenu() {
        // ── Título traduzível via Adventure API ───────────────────────────────
        Component title = Component.translatable("aurun.menu.title")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);

        // ── Cria o inventário Chest de 27 slots ───────────────────────────────
        Inventory inventory = org.bukkit.Bukkit.createInventory(null, 27, title);

        // ── Preenche bordas com painéis de vidro preto decorativos ───────────
        ItemStack borderPanel = buildBorderPanel();
        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, borderPanel);
        }

        // ── Botão: Modo Aleatório (Slot 0) ────────────────────────────────────
        inventory.setItem(SLOT_RANDOM, buildRandomModeItem());

        // ── Botão: Stop / Silenciar (Slot 8) ─────────────────────────────────
        inventory.setItem(SLOT_STOP, buildStopItem());

        // ── Botões de faixas individuais (slots 10 a 16 + 18 a 25) ───────────
        // Começa no slot 10 (linha 2, posição 2) e pula slots de borda
        MusicTrack[] tracks = MusicTrack.values();
        int[] musicSlots = getMusicSlots();

        for (int i = 0; i < tracks.length && i < musicSlots.length; i++) {
            inventory.setItem(musicSlots[i], buildTrackItem(tracks[i]));
        }

        return inventory;
    }

    /**
     * Retorna os slots disponíveis para botões de músicas individuais.
     * Layout: linha 2 (slots 10-16) e linha 3 (slots 19-25), excluindo bordas.
     */
    public static int[] getMusicSlots() {
        return new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── CONSTRUTORES DE ITEMS ─────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constrói o item do botão de Modo Aleatório.
     * Material: JUKEBOX — ícone intuitivo para música aleatória.
     * Nome e lore usam Component.translatable() para suporte a Geyser.
     */
    private ItemStack buildRandomModeItem() {
        ItemStack item = new ItemStack(Material.JUKEBOX);
        ItemMeta meta = item.getItemMeta();

        // Nome traduzível: chave "aurun.menu.random.name"
        meta.displayName(
                Component.translatable("aurun.menu.random.name")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
        );

        // Lore traduzível com duas linhas descritivas
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("aurun.menu.random.lore1")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.translatable("aurun.menu.random.lore2")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Constrói o item do botão de Stop.
     * Material: BARRIER — representa bloqueio/parada de forma intuitiva.
     * Ao clicar, para a música e permite que o Minecraft retome seu ciclo natural.
     */
    private ItemStack buildStopItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        // Nome traduzível: chave "aurun.menu.stop.name"
        meta.displayName(
                Component.translatable("aurun.menu.stop.name")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
        );

        // Lore traduzível
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("aurun.menu.stop.lore1")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.translatable("aurun.menu.stop.lore2")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Constrói o item clicável de uma faixa musical específica.
     *
     * Material: MUSIC_DISC_CAT — representa uma faixa do catálogo.
     * Nome e lore usam Component.translatable() referenciando chaves do Resource Pack.
     *
     * @param track A faixa musical a representar.
     * @return ItemStack configurado com nome e lore traduzíveis.
     */
    private ItemStack buildTrackItem(MusicTrack track) {
        ItemStack item = new ItemStack(Material.MUSIC_DISC_CAT);
        ItemMeta meta = item.getItemMeta();

        // Nome traduzível: chave "aurun.music.<commandId>" (ex: "aurun.music.aurora")
        meta.displayName(
                Component.translatable(track.getTranslationKey())
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, false)
        );

        // Lore com instrução e identificador da faixa
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("aurun.menu.track.lore1")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        // Segunda linha: exibe o commandId como dica de commando
        lore.add(Component.text("/armusic start " + track.getCommandId())
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Constrói o painel de vidro preto decorativo para as bordas do menu.
     * Sem nome visível (nome vazio para não poluir a UI).
     */
    private ItemStack buildBorderPanel() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        // Nome vazio com cor para não mostrar o nome padrão do item
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
