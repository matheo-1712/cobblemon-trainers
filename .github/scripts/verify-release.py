"""Validate the merged Fabric artifact before publishing it to any destination."""

import json
import sys
from zipfile import ZipFile


def verify(path, version):
    with ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate entries in the release jar")
        metadata = json.loads(jar.read("fabric.mod.json"))
        if metadata["id"] != "cobblemon-trainers" or metadata["version"] != version:
            raise ValueError("Release jar ID/version does not match the requested release")
        for side in ("main", "client"):
            for entry in metadata["entrypoints"][side]:
                name = entry["value"] if isinstance(entry, dict) else entry
                jar.getinfo(name.replace(".", "/") + ".class")
        for service in ("platform.TrainerPlatform", "client.platform.TrainerClientPlatform"):
            descriptor = "META-INF/services/matheo1712.cobbletrainers." + service
            implementations = [line.split("#", 1)[0].strip() for line in jar.read(descriptor).decode().splitlines()]
            implementations = [name for name in implementations if name]
            if len(implementations) != 1:
                raise ValueError("Expected exactly one platform service: " + descriptor)
            jar.getinfo(implementations[0].replace(".", "/") + ".class")
        for config in metadata["mixins"]:
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
    print(f"Verified Fabric release {version}: {path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: verify-release.py JAR VERSION")
    verify(sys.argv[1], sys.argv[2])
