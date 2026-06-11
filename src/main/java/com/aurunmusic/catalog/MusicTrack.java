package com.aurunmusic.catalog;

/**
 * Catálogo central de todas as músicas autorais do servidor.
 *
 * Cada constante do enum representa uma faixa musical. Contém:
 * - {@code commandId}   : identificador usado nos comandos (/armusic start <commandId>)
 *                         e como chave de tradução Adventure no formato "aurun.music.<commandId>".
 * - {@code soundKey}    : namespace completo do som, registrado no sounds.json do Resource Pack
 *                         (ex: "aurun:music.aurora"). O cliente processa 100% o .ogg localmente.
 * - {@code durationTicks}: duração exata da faixa em ticks (20 ticks = 1 segundo).
 *                          Usada pelo Modo Aleatório para agendar a próxima música no momento exato.
 *
 * ── COMO ADICIONAR UMA NOVA MÚSICA ────────────────────────────────────────────
 *  1. Coloque o arquivo .ogg no Resource Pack em: assets/aurun/sounds/music/<arquivo>.ogg
 *  2. Registre no sounds.json do Resource Pack com a chave "music.<arquivo>".
 *  3. Adicione aqui uma nova constante do enum.
 *  4. Adicione a chave de tradução "aurun.music.<commandId>" nos arquivos .json de idioma
 *     (pt_br.json, en_us.json, etc.) no Resource Pack.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public enum MusicTrack {

    // ── Faixas do catálogo ────────────────────────────────────────────────────
    // Ajuste o durationTicks conforme a duração real de cada .ogg.
    // Fórmula: duração_em_segundos * 20 = ticks
    CAVERNA(
            "caverna", 
            "aurun:music.a_caverna_profunda", 
            4234L
    ),
    NETHER(
            "nether", 
            "aurun:music.aurun_mc_-_salto_quntico_no_nether_001", 
            5435L
    ),
    MAR(
            "mar", 
            "aurun:music.aurun_mc_no_mar_profundo_001", 
            2424L
    ),
    WORLD_NORMAL(
            "world_normal", 
            "aurun:music.aurun_world_001", 
            4598L
    ),
    WORLD_PARTY(
            "world_party", 
            "aurun:music.aurun_world_002", 
            4149L
    ),
    JARDIM(
            "jardim", 
            "aurun:music.aurunmc_-_jardim_plido_flow_-_escolha", 
            5253L
    ),
    MEU_SERVER(
            "meu_server", 
            "aurun:music.meu_server_aurun_mc_world_-_escolhida", 
            4755L
    ),
    MUNDO_AURUN(
            "mundo_aurun", 
            "aurun:music.o_mundo_do_aurun_", 
            4797L
    );

    // ── Campos ────────────────────────────────────────────────────────────────
    private final String commandId;
    private final String soundKey;
    private final long durationTicks;

    // ── Construtor ────────────────────────────────────────────────────────────
    MusicTrack(String commandId, String soundKey, long durationTicks) {
        this.commandId = commandId;
        this.soundKey = soundKey;
        this.durationTicks = durationTicks;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** ID usado no comando /armusic start <commandId> e na chave de tradução Adventure. */
    public String getCommandId() {
        return commandId;
    }

    /**
     * Namespace completo do som registrado no sounds.json do Resource Pack.
     * Formato: "namespace:caminho.do.som"
     */
    public String getSoundKey() {
        return soundKey;
    }

    /** Duração da faixa em ticks do servidor (20 ticks = 1 segundo). */
    public long getDurationTicks() {
        return durationTicks;
    }

    /**
     * Chave de tradução Adventure para o nome desta faixa.
     * Formato: "aurun.music.<commandId>"
     * Esta chave deve existir nos arquivos .json do Resource Pack.
     */
    public String getTranslationKey() {
        return "aurun.music." + commandId;
    }

    /**
     * Busca uma faixa pelo seu commandId (case-insensitive).
     * Retorna null se não encontrada.
     *
     * @param id O commandId a buscar (ex: "aurora").
     * @return A MusicTrack correspondente, ou null.
     */
    public static MusicTrack fromCommandId(String id) {
        for (MusicTrack track : values()) {
            if (track.commandId.equalsIgnoreCase(id)) {
                return track;
            }
        }
        return null;
    }
}
