package com.aurunmusic.commands;

import com.aurunmusic.AurunMusicPlugin;
import com.aurunmusic.catalog.MusicTrack;
import com.aurunmusic.managers.GuiManager;
import com.aurunmusic.managers.MusicManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executor e TabCompleter do comando principal /armusic.
 *
 * Subcomandos disponíveis:
 * - /armusic menu            → Abre a GUI de baú (27 slots).
 * - /armusic stop            → Para música atual e desativa o Modo Aleatório.
 * - /armusic start random    → Ativa o Modo Aleatório.
 * - /armusic start <musica>  → Reproduz uma faixa específica do catálogo.
 *
 * Todas as mensagens de feedback usam Component.translatable() para suporte
 * nativo a múltiplos idiomas via Resource Pack (indispensável para Geyser).
 */
public class ArMusicCommand implements CommandExecutor, TabCompleter {

    private final AurunMusicPlugin plugin;
    private final MusicManager musicManager;
    private final GuiManager guiManager;

    public ArMusicCommand(AurunMusicPlugin plugin, MusicManager musicManager) {
        this.plugin = plugin;
        this.musicManager = musicManager;
        this.guiManager = new GuiManager();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── EXECUTOR DO COMANDO ───────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        // ── Somente jogadores podem usar este comando ─────────────────────────
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.translatable("aurun.cmd.player_only")
                    .color(NamedTextColor.RED));
            return true;
        }

        // ── Verifica permissão básica ─────────────────────────────────────────
        if (!player.hasPermission("aurunmusic.use")) {
            player.sendMessage(Component.translatable("aurun.cmd.no_permission")
                    .color(NamedTextColor.RED));
            return true;
        }

        // ── Sem argumentos: mostra uso ────────────────────────────────────────
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "menu"  -> handleMenu(player);
            case "stop"  -> handleStop(player);
            case "start" -> handleStart(player, args);
            default      -> { sendUsage(player); yield true; }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── HANDLERS DE SUBCOMANDOS ───────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * /armusic menu → Abre o inventário GUI de 27 slots.
     */
    private boolean handleMenu(Player player) {
        player.openInventory(guiManager.buildMenu());
        return true;
    }

    /**
     * /armusic stop → Para música e desativa modo aleatório.
     * Feedback traduzível: "aurun.cmd.stop.success"
     */
    private boolean handleStop(Player player) {
        musicManager.stopMusic(player);
        player.sendMessage(
                Component.translatable("aurun.cmd.stop.success")
                        .color(NamedTextColor.YELLOW)
        );
        return true;
    }

    /**
     * /armusic start <musica|random>
     *
     * Casos:
     * - "random"          → Ativa Modo Aleatório.
     * - <commandId válido> → Reproduz faixa específica.
     * - <desconhecido>    → Erro com lista de opções.
     */
    private boolean handleStart(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        String target = args[1].toLowerCase();

        // Caso especial: modo aleatório
        if (target.equals("random")) {
            musicManager.startRandomMode(player);
            player.sendMessage(
                    Component.translatable("aurun.cmd.random.start")
                            .color(NamedTextColor.AQUA)
            );
            return true;
        }

        // Tenta encontrar a faixa pelo commandId
        MusicTrack track = MusicTrack.fromCommandId(target);
        if (track == null) {
            // Faixa não encontrada: envia mensagem de erro traduzível
            player.sendMessage(
                    Component.translatable("aurun.cmd.track.not_found")
                            .color(NamedTextColor.RED)
            );
            return true;
        }

        // Reproduz a faixa específica
        musicManager.playTrack(player, track);

        // Feedback: nome da faixa via Component.translatable() do catálogo
        player.sendMessage(
                Component.translatable("aurun.cmd.track.playing",
                        Component.translatable(track.getTranslationKey())
                                .color(NamedTextColor.GREEN))
                        .color(NamedTextColor.YELLOW)
        );

        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── TAB COMPLETER INTELIGENTE ─────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TabCompleter que sugere subcomandos e argumentos dinamicamente.
     *
     * Nível 1 (args[0]): "menu", "stop", "start"
     * Nível 2 (args[1], só para "start"): "random" + todos os commandIds do catálogo
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {

        if (!(sender instanceof Player player)) return List.of();
        if (!player.hasPermission("aurunmusic.use")) return List.of();

        // ── Nível 1: subcomandos ──────────────────────────────────────────────
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("menu", "stop", "start");
            return filterByPrefix(subCommands, args[0]);
        }

        // ── Nível 2: argumentos do subcomando "start" ─────────────────────────
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> startOptions = new ArrayList<>();
            startOptions.add("random");

            // Adiciona todos os commandIds do catálogo ao autocompletar
            for (MusicTrack track : MusicTrack.values()) {
                startOptions.add(track.getCommandId());
            }

            return filterByPrefix(startOptions, args[1]);
        }

        return List.of();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ── UTILITÁRIOS ───────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Filtra uma lista de strings pelo prefixo digitado pelo jogador.
     * Case-insensitive para melhor experiência de uso.
     *
     * @param options Lista completa de opções.
     * @param prefix  Texto já digitado pelo jogador.
     * @return Lista filtrada pelas opções que começam com o prefixo.
     */
    private List<String> filterByPrefix(List<String> options, String prefix) {
        return options.stream()
                .filter(opt -> opt.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Envia uma mensagem de uso traduzível ao jogador.
     * Chave: "aurun.cmd.usage"
     */
    private void sendUsage(Player player) {
        player.sendMessage(
                Component.translatable("aurun.cmd.usage")
                        .color(NamedTextColor.GOLD)
        );
    }
}
