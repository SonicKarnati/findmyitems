# Run while standing at the origin of the documented fixture area.
setblock ~2 ~ ~2 minecraft:chest[facing=north]
item replace block ~2 ~ ~2 container.0 with minecraft:white_bed 1
item replace block ~2 ~ ~2 container.1 with minecraft:bedrock 1

setblock ~4 ~ ~2 minecraft:chest[facing=north]
setblock ~4 ~ ~1 minecraft:stone
item replace block ~4 ~ ~2 container.0 with minecraft:diamond 32

setblock ~6 ~ ~2 minecraft:chest[facing=north]
setblock ~5 ~ ~1 minecraft:stone
setblock ~7 ~ ~1 minecraft:stone
item replace block ~6 ~ ~2 container.0 with minecraft:emerald 5

setblock ~8 ~ ~2 minecraft:chest[facing=north]
setblock ~9 ~ ~2 minecraft:chest[facing=north]
item replace block ~8 ~ ~2 container.0 with minecraft:iron_ingot 64
item replace block ~9 ~ ~2 container.0 with minecraft:iron_ingot 64

setblock ~12 ~ ~2 minecraft:chest[facing=north]
setblock ~12 ~1 ~2 minecraft:hopper[facing=down]
item replace block ~12 ~1 ~2 container.0 with minecraft:gold_ingot 16

setblock ~14 ~ ~2 minecraft:crafting_table

setblock ~16 ~ ~2 minecraft:chest[facing=north]
item replace block ~16 ~ ~2 container.0 with minecraft:diamond 2
item replace block ~16 ~ ~2 container.1 with minecraft:stick 2
item replace block ~16 ~ ~2 container.2 with minecraft:oak_log 3

setblock ~30 ~ ~2 minecraft:chest[facing=north]
item replace block ~30 ~ ~2 container.0 with minecraft:diamond 8
