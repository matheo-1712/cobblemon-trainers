package matheo1712.cobbletrainers.battlephone

import matheo1712.cobbletrainers.block.TrainerBlocks

/**
 * The battle phone, kept apart from the rest of the mod: what will become Cobblemon BattlePhone,
 * a mod of its own built on the trainers this one provides.
 *
 * Until it moves out, it is started by the loader entrypoints next to the mod itself - never
 * from [matheo1712.cobbletrainers.CobblemonTrainers], which must not know it exists. The
 * `verifyApiBoundary` task holds that line: nothing outside this package imports from it.
 */
object BattlePhone {

    /** Items, packets and the creative tab entries. Called right after the mod's own init. */
    fun onInitialize() {
        BattlePhoneItems.register()
        BattlePhoneNetworking.register()
        // Listed before the spawner, where they have always been.
        TrainerBlocks.addToTab(0, BattlePhoneItems.BATTLE_PHONES)
    }
}
