# Safe from any player position: remove only blocks owned by fixture markers.
execute as @e[type=minecraft:marker,tag=findmyitems_fixture,tag=findmyitems_fixture_chest] at @s if block ~ ~ ~ minecraft:chest run setblock ~ ~ ~ minecraft:air
execute as @e[type=minecraft:marker,tag=findmyitems_fixture,tag=findmyitems_fixture_stone] at @s if block ~ ~ ~ minecraft:stone run setblock ~ ~ ~ minecraft:air
execute as @e[type=minecraft:marker,tag=findmyitems_fixture,tag=findmyitems_fixture_hopper] at @s if block ~ ~ ~ minecraft:hopper run setblock ~ ~ ~ minecraft:air
execute as @e[type=minecraft:marker,tag=findmyitems_fixture,tag=findmyitems_fixture_crafting_table] at @s if block ~ ~ ~ minecraft:crafting_table run setblock ~ ~ ~ minecraft:air
kill @e[type=minecraft:marker,tag=findmyitems_fixture]
