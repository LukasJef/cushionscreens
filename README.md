# CushionScreens - build

Verze v `gradle.properties` odpovidaji manifestu puvodniho jaru
(Fabric Loom 1.17.14, Fabric Loader 0.19.3, Minecraft 26.3-snapshot-3,
Fabric API 0.155.0+26.3).

## Dulezite: Minecraft 26.x je neobfuskovany

Od verze 26.1 uz Minecraft (i snapshoty) nepouziva zadne obfuskovane
nazvy trid/metod, takze:
- Loom plugin se jmenuje `net.fabricmc.fabric-loom` (ne uz `fabric-loom`).
- V `dependencies` se **nepouziva** zadny radek `mappings ...` - kdyz tam
  je, dostanes chybu `Failed to find official mojang mappings for ...`.
- Pouziva se obycejne `implementation`/`compileOnly` misto
  `modImplementation`/`modCompileOnly` (Loom uz nemusi nic remapovat).

Tenhle projekt uz je takhle nastaveny. Pokud narazis na chybu zminujici
mappings, zkontroluj, ze `build.gradle` neobsahuje zadny `mappings` radek.

## Java 25 je povinna

Tahle verze Minecraftu/Loomu vyzaduje bezet primo na JDK 25 (ne jen jako
"target", ale i Gradle samotny musi bezet na Jave 25 - jinak muzes dostat
i tu samou chybu s mappings, nebo `error: release version 25 not
supported`). V `settings.gradle` je proto zapnuty
`foojay-resolver-convention` plugin, ktery Gradle nechá **automaticky
stahnout spravnou JDK 25**, i kdyz zadnou nemas nainstalovanou - staci mit
pripojeni k internetu pri prvnim buildu.

Pokud bys chtel pouzit vlastni uz nainstalovanou JDK 25 misto stazene,
nastav `JAVA_HOME` na jeji cestu pred spustenim `gradlew`.

## Prvni spusteni

Tenhle balicek neobsahuje binarni `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`
(nemam k nim odsud pristup ke stazeni). Mas dve moznosti:

1. **Mas uz Gradle nainstalovany lokalne**: staci spustit primo
   ```
   gradle wrapper --gradle-version 9.6.1
   ```
   Tim se v projektu vygeneruji `gradlew`, `gradlew.bat` a
   `gradle/wrapper/gradle-wrapper.jar` presne pro verzi z manifestu, a
   pak uz muzes normalne pouzivat `./gradlew build`.

2. **Nemas Gradle nainstalovany**: nainstaluj si ho (napr. pres SDKMAN:
   `sdk install gradle 9.6.1`), pak krok 1.

## Build

```
./gradlew build
```

Vysledny jar bude v `build/libs/cushionscreens-<verze>.jar`.

## Poznamka k Fabric API verzi

`fabric_version` v `gradle.properties` je natvrdo `0.155.0+26.3` - presne
odpovida snapshotu `26.3-snapshot-3`. Pokud budes hru/Fabric Loader
aktualizovat na novejsi snapshot, zvys i tohle cislo (najdes ho na
https://github.com/FabricMC/fabric-api/releases nebo na Modrinth/CurseForge
strance Fabric API - hledej build oznaceny stejnym snapshotem jako tva hra).

