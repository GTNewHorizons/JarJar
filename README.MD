# JarJar

A RFB Plugin to improve FML for 1.7.10.
* Adds Nested Jar (Jar-in-Jar) support
* Picks the latest version of a coremod/mod to load if multiple are found
* Adds a ParallelModDiscoverer to speed up mod loading
* Updates ASMDataTable to also capture interfaces like later versions of FML/Forge do
* Includes various speedups from various sources, including CoreTweaks and newer versions of FML/Forge


Inspired by and/or code adapted from (see LICENSE for more details):
* ForgeModLoader
* Fabric-loader
* FancyModLoader
* CoreTweaks


## Notes:
* Minecraft 1.7.10 only.
* Requires [RetroFuturaBootstrap](https://github.com/GTNewHorizons/RetroFuturaBootstrap)
* Has only been tested with the mods included in the GT New Horizons modpack.
