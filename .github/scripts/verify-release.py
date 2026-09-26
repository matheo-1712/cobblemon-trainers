"""Validate a loader artifact before publishing it to any destination."""

import json
import sys
import tomllib
from zipfile import ZipFile


def verify(path, version, loader="fabric"):
    with ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate entries in the release jar")
        if loader == "fabric":
            metadata = json.loads(jar.read("fabric.mod.json"))
            mod_id, mod_version = metadata["id"], metadata["version"]
            expected_id = "cobblemon-trainers"
            configs = metadata["mixins"]
            for side in ("main", "client"):
                for entry in metadata["entrypoints"][side]:
                    name = entry["value"] if isinstance(entry, dict) else entry
                    jar.getinfo(name.replace(".", "/") + ".class")
            if "META-INF/neoforge.mods.toml" in names:
                raise ValueError("NeoForge metadata in Fabric jar")
        elif loader == "neoforge":
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
            mod_id, mod_version = metadata["mods"][0]["modId"], metadata["mods"][0]["version"]
            expected_id = "cobblemon_trainers"
            configs = [entry["config"] for entry in metadata["mixins"]]
            for name in ("CobblemonTrainersNeoForge", "CobblemonTrainersNeoForgeClient"):
                jar.getinfo(f"matheo1712/cobbletrainers/neoforge/{name}.class")
            if "fabric.mod.json" in names or any("/fabric/" in name and name.endswith(".class") for name in names):
                raise ValueError("Fabric content in NeoForge jar")
        else:
            raise ValueError("Unknown loader: " + loader)
        if mod_id != expected_id or mod_version != version:
            raise ValueError("Release jar ID/version does not match the requested release")
        for service in ("platform.TrainerPlatform", "client.platform.TrainerClientPlatform"):
            descriptor = "META-INF/services/matheo1712.cobbletrainers." + service
            implementations = [line.split("#", 1)[0].strip() for line in jar.read(descriptor).decode().splitlines()]
            implementations = [name for name in implementations if name]
            if len(implementations) != 1:
                raise ValueError("Expected exactly one platform service: " + descriptor)
            if f".cobbletrainers.{loader}." not in implementations[0]:
                raise ValueError("Wrong loader implementation: " + descriptor)
            jar.getinfo(implementations[0].replace(".", "/") + ".class")
        for config in configs:
            mixins = json.loads(jar.read(config))
            for name in mixins.get("mixins", []) + mixins.get("client", []):
                jar.getinfo((mixins["package"] + "." + name).replace(".", "/") + ".class")
        jar.getinfo("matheo1712/cobbletrainers/CobblemonTrainers.class")
        jar.getinfo("matheo1712/cobbletrainers/battle/ai/TrainerBattleAI.class")
        jar.getinfo("assets/cobblemon-trainers/lang/en_us.json")
        for color in ("black", "green", "pink", "red", "white", "yellow"):
            jar.getinfo(f"assets/cobblemon-trainers/models/item/battle_phone_{color}.json")
            jar.getinfo(f"assets/cobblemon-trainers/textures/gui/battle_phone/{color}/frame.png")
        if any(name.startswith("data/cobblemonrlm/") for name in names):
            raise ValueError("Example trainers must not be bundled in the mod")
    print(f"Verified {loader} release {version}: {path}")


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        raise SystemExit("Usage: verify-release.py JAR VERSION [fabric|neoforge]")
    verify(*sys.argv[1:])
