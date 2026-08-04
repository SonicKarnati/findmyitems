# Run while standing at the origin of the documented fixture area.
# Only air is claimed. The marker makes setup repeatable and tells reset which blocks it owns.
execute positioned ~2 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_chest"]}
execute positioned ~2 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:chest[facing=north]
execute positioned ~2 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.0 with minecraft:white_bed 1
execute positioned ~2 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.1 with minecraft:bedrock 1

execute positioned ~4 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_chest"]}
execute positioned ~4 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:chest[facing=north]
execute positioned ~4 ~ ~1 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_stone"]}
execute positioned ~4 ~ ~1 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:stone
execute positioned ~4 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.0 with minecraft:diamond 32

execute positioned ~6 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_chest"]}
execute positioned ~6 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:chest[facing=north]
execute positioned ~5 ~ ~1 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_stone"]}
execute positioned ~5 ~ ~1 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:stone
execute positioned ~7 ~ ~1 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_stone"]}
execute positioned ~7 ~ ~1 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:stone
execute positioned ~6 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.0 with minecraft:emerald 5

execute positioned ~8 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_chest"]}
execute positioned ~8 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:chest[facing=north]
execute positioned ~9 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_chest"]}
execute positioned ~9 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:chest[facing=north]
execute positioned ~8 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.0 with minecraft:iron_ingot 64
execute positioned ~9 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.0 with minecraft:iron_ingot 64

execute positioned ~12 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_chest"]}
execute positioned ~12 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:chest[facing=north]
execute positioned ~12 ~1 ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_hopper"]}
execute positioned ~12 ~1 ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:hopper[facing=down]
execute positioned ~12 ~1 ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.0 with minecraft:gold_ingot 16

execute positioned ~14 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_crafting_table"]}
execute positioned ~14 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:crafting_table

execute positioned ~16 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_chest"]}
execute positioned ~16 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:chest[facing=north]
execute positioned ~16 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.0 with minecraft:diamond 3
execute positioned ~16 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.1 with minecraft:stick 2
execute positioned ~16 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.2 with minecraft:oak_log 3

execute positioned ~30 ~ ~2 unless entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] if block ~ ~ ~ minecraft:air run summon minecraft:marker ~ ~ ~ {Tags:["findmyitems_fixture","findmyitems_fixture_chest"]}
execute positioned ~30 ~ ~2 if block ~ ~ ~ minecraft:air run setblock ~ ~ ~ minecraft:chest[facing=north]
execute positioned ~30 ~ ~2 if entity @e[type=minecraft:marker,tag=findmyitems_fixture,distance=..0.1] run item replace block ~ ~ ~ container.0 with minecraft:diamond 8
