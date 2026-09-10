package matheo1712.cobbletrainers.intro

import com.google.gson.annotations.SerializedName
import matheo1712.cobbletrainers.CobblemonTrainers
import net.minecraft.resources.ResourceLocation

/**
 * An intro: the screen a trainer is announced with, written by whoever ships the pack.
 *
 * It is a **scene in layers**. The mod owns none of it - the backdrop is a `fill` like any
 * other - and draws them in the order they are written, the first one behind. What each layer
 * is, and what it may say, is in [IntroLayer].
 *
 * A trainer names one through `battle.intro`, and several trainers may name the same: an intro
 * is a file of its own, in `data/<namespace>/cobblemontrainers_intros/`, so a whole league
 * shares one rather than copying it into every trainer.
 *
 * Everything is optional, as everywhere else in the mod's JSON: `{}` is a valid - if empty -
 * intro. Which is also what makes Gson build these through the no-argument constructor Kotlin
 * generates, so a missing block never arrives null.
 *
 * @param duration How long the screen stays up, in ticks. The battle behind it opens when they
 *   run out - see [matheo1712.cobbletrainers.battle.TrainerBattleIntro].
 * @param fadeIn Ticks the whole screen takes to arrive over the world.
 * @param fadeOut Ticks it takes to hand it back.
 * @param layers The scene, drawn in the order written.
 */
data class TrainerIntro(
    val duration: Int = DEFAULT_TICKS,
    val fadeIn: Int = DEFAULT_FADE_IN,
    val fadeOut: Int = DEFAULT_FADE_OUT,
    val layers: List<IntroLayer> = emptyList()
) {

    /** The duration, held to what reads as a screen rather than a flash or a wait. */
    fun ticks(): Int = duration.coerceIn(MIN_TICKS, MAX_TICKS)

    /** The tick the last entrance finishes on, which is when the screen may be skipped. */
    fun entranceEnd(): Int =
        layers.maxOfOrNull { it.at + it.length }?.coerceIn(0, ticks()) ?: 0

    /** Whether any layer needs the trainer's team sent along - only those cost a lookup. */
    fun needsTeam(): Boolean = layers.any { it.type == IntroLayer.POKEMON }

    /**
     * Drops what cannot be drawn and says so, once, at load. A layer the client would not know
     * what to do with never reaches it: what is left is an intro that plays.
     */
    fun validated(id: ResourceLocation): TrainerIntro {
        if (duration != ticks()) {
            CobblemonTrainers.LOGGER.warn(
                "Intro {}: duration is {} ticks, using {}. Expected {} to {}.",
                id, duration, ticks(), MIN_TICKS, MAX_TICKS
            )
        }

        return copy(layers = layers.filter { it.validate(id) })
    }

    companion object {
        /** What an intro lasts when it does not say: five seconds. */
        const val DEFAULT_TICKS = 100
        const val DEFAULT_FADE_IN = 4
        const val DEFAULT_FADE_OUT = 8

        const val MIN_TICKS = 20
        const val MAX_TICKS = 200

        /**
         * The screen an intro is written on. Offsets and sizes are its pixels, scaled to the
         * window at draw time, so an intro written on a wide screen still holds on a small one.
         * Anchors are not scaled - they land on the real edges.
         */
        const val REFERENCE_WIDTH = 640
        const val REFERENCE_HEIGHT = 360
    }
}

/**
 * One layer of an intro.
 *
 * The fields fall in two halves: what every layer has - where it sits, when it arrives and how
 * - and what only its own [type] reads. Everything has a default, so a layer says only what it
 * changes.
 *
 * @param type What is drawn. One of [TYPES]; anything else is dropped at load.
 * @param anchor Which of the nine points of the screen the layer puts its centre on.
 * @param offset Where it goes from there, `[x, y]`, in the reference screen's pixels.
 * @param at The tick its entrance starts on.
 * @param length How many ticks that entrance lasts. Named `for` in the JSON, which Kotlin
 *   cannot spell as an identifier.
 * @param from Where it comes from: a side to slide in from, or `fade`, `pop`, `none`.
 * @param ease How the entrance is timed.
 * @param alpha Its opacity once it has arrived.
 * @param sound Played once, when the entrance starts. A UI sound, so it rides the *Master*
 *   slider rather than the music one the battle theme is on.
 * @param who Whose figure, or whose team: `trainer` or `player`.
 * @param width Width in reference pixels. A `fill` without one spans the screen.
 * @param height Height in reference pixels: how tall a model stands, how thick a band is.
 * @param color `#RRGGBB`. What it colours depends on the type - a fill, a text, an image tint.
 * @param slant How far a `fill` leans over its own height. 0 is a rectangle.
 * @param value The text of a `text` layer: a translation key or plain words, either of which
 *   may carry the placeholders listed in [PLACEHOLDERS].
 * @param size A text or VS scale, 1 being the game's own font.
 * @param shadow Whether that text is drawn with its drop shadow.
 * @param texture The image of an `image` layer. It is read by the *client*, so it ships under
 *   `assets/` - the same rule as battle music.
 * @param yaw Which way a model is turned, in degrees. 0 faces the player.
 * @param tilt How far it looks up or down, in degrees.
 * @param slot Which Pokémon of the team a `pokemon` layer draws, from 1.
 * @param slots How many ball slots a `team_balls` layer lays out.
 * @param gap Reference pixels between two of them.
 * @param empty Whether the slots the team does not fill are drawn dimmed, or not at all.
 */
data class IntroLayer(
    val type: String = "",
    val anchor: String = "center",
    val offset: List<Int> = emptyList(),
    val at: Int = 0,
    @SerializedName("for") val length: Int = DEFAULT_ENTRANCE,
    val from: String = "fade",
    val ease: String = "out",
    val alpha: Float = 1f,
    val sound: String? = null,
    val volume: Float = 1f,
    val pitch: Float = 1f,

    val who: String = "trainer",
    val width: Int? = null,
    val height: Int? = null,
    val color: String? = null,
    val slant: Float = 0f,
    val value: String? = null,
    val size: Float = 1f,
    val shadow: Boolean = true,
    val texture: String? = null,
    val yaw: Float = 0f,
    val tilt: Float = 0f,
    val slot: Int = 1,
    val slots: Int = 6,
    val gap: Int = 3,
    val empty: Boolean = true
) {

    val offsetX: Int get() = offset.getOrElse(0) { 0 }
    val offsetY: Int get() = offset.getOrElse(1) { 0 }

    /** Whether this layer is the player's rather than the trainer's. */
    val isPlayer: Boolean get() = who.equals(PLAYER, ignoreCase = true)

    /**
     * Whether the layer can be drawn at all, warning about what is wrong with it otherwise.
     *
     * Only the two questions a client cannot answer for itself are asked: is this a layer we
     * know, and does it carry what its own type cannot do without. Everything else has a
     * default to fall back on, and falling back quietly on a colour is better than dropping a
     * layer over it.
     */
    fun validate(id: ResourceLocation): Boolean {
        if (type !in TYPES) {
            CobblemonTrainers.LOGGER.warn(
                "Intro {}: unknown layer type '{}', ignoring it. Expected one of: {}",
                id, type, TYPES.joinToString(", ")
            )
            return false
        }

        if (type == IMAGE && (texture.isNullOrBlank() || width == null || height == null)) {
            CobblemonTrainers.LOGGER.warn(
                "Intro {}: an image layer needs a texture, a width and a height. Ignoring it.",
                id
            )
            return false
        }

        if (anchor !in ANCHORS) {
            CobblemonTrainers.LOGGER.warn(
                "Intro {}: unknown anchor '{}', centring instead. Expected one of: {}",
                id, anchor, ANCHORS.joinToString(", ")
            )
        }

        if (from !in ENTRANCES) {
            CobblemonTrainers.LOGGER.warn(
                "Intro {}: unknown entrance '{}', fading instead. Expected one of: {}",
                id, from, ENTRANCES.joinToString(", ")
            )
        }

        return true
    }

    companion object {
        const val FIGURE = "figure"
        const val TEXT = "text"
        const val IMAGE = "image"
        const val FILL = "fill"
        const val VS = "vs"
        const val TEAM_BALLS = "team_balls"
        const val POKEMON = "pokemon"

        val TYPES: List<String> = listOf(FIGURE, TEXT, IMAGE, FILL, VS, TEAM_BALLS, POKEMON)

        const val PLAYER = "player"

        /** The nine points of the screen, read as `vertical-horizontal`. */
        val ANCHORS: List<String> = listOf(
            "top-left", "top", "top-right",
            "left", "center", "right",
            "bottom-left", "bottom", "bottom-right"
        )

        val ENTRANCES: List<String> = listOf("left", "right", "top", "bottom", "fade", "pop", "none")

        /** What a `text` layer may put in its `value`, replaced by the client. */
        val PLACEHOLDERS: List<String> = listOf("%name%", "%category%", "%level%", "%team%", "%player%")

        /** Ticks an entrance takes when it does not say. */
        const val DEFAULT_ENTRANCE = 12
    }
}
