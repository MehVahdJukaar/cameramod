plugins {
    id("com.possible-triangle.neoforge")
}

neoforge {
    dependOn(project(":common"))
    accessWidener(project(":common"))
}

val moonlight_version: String by extra
val supplementaries_version: String by extra
val mixin_squared_version: String by extra
val sable_companion_version: String by extra
val codecui_version: String by extra

dependencies {

    modImplementation("net.mehvahdjukaar:moonlight-neoforge:${moonlight_version}")

    modRuntimeOnly("net.mehvahdjukaar:codecui-neoforge:${codecui_version}")
    accessTransformers("net.mehvahdjukaar:moonlight-neoforge:${moonlight_version}")

    annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-common:${mixin_squared_version}")
    implementation("com.github.bawnorton.mixinsquared:mixinsquared-common:${mixin_squared_version}")
    apiInclude("com.github.bawnorton.mixinsquared:mixinsquared-forge:${mixin_squared_version}")
    implementation("com.github.bawnorton.mixinsquared:mixinsquared-forge:${mixin_squared_version}")


    modCompileOnly("curse.maven:supplementaries-412082:8051628")
    modCompileOnly("curse.maven:quark-243121:7640331")
    modCompileOnly("curse.maven:zeta-968868:7640154")

    //modImplementation("cc.tweaked-cobalt:cobalt:0.93")
    modImplementation("curse.maven:exposure-871755:8223555")
    modCompileOnly("curse.maven:cc-tweaked-282001:5714512")
    modCompileOnly("curse.maven:jei-238222:7420587")
    modRuntimeOnly("maven.modrinth:jade:15.10.5+neoforge")
    modImplementation("curse.maven:sodium-394468:8382328")
    modCompileOnly("curse.maven:irisshaders-455508:6213635")
    modCompileOnly("curse.maven:distant-horizons-508933:8287411")
    modCompileOnly("maven.modrinth:iris:1.8.8+1.21.1-neoforge")

    modImplementation("curse.maven:geckolib-388172:7707149")
    modCompileOnly("curse.maven:watermedia-869524:7072353")

    modImplementation("curse.maven:vampirism-become-a-vampire-233029:8105838")
    modImplementation("curse.maven:supernatural-610880:8275088")
    modImplementation("curse.maven:origins-neoforge-1375372:8179055")
    modImplementation("curse.maven:jupiter-1072905:7738312") // required by Origins (NeoForge)

    //  modImplementation("cc.tweaked:cc-tweaked-1.21.1-forge:1.117.0")
    modCompileOnly("curse.maven:quark-243121:7640331")
    modCompileOnly("curse.maven:simple-clouds-1121215:6928979")

    modImplementation("curse.maven:refurbished-furniture-897116:7473565")
    modImplementation("curse.maven:framework-549225:7462477")

    // henkelmax's Camera Mod: NeoForge only on 1.21.1, so the picture tape integration lives here.
    // It shadows corelib into its own package, so no extra dependency is needed.
    modImplementation("curse.maven:camera-mod-289310:6715623")

    modImplementation("foundry.veil:veil-neoforge-1.21.1:4.4.1")
    modImplementation("curse.maven:fsable-1312371:8263584")

    // Light-beam mods to check against sublevel view finder feeds: one of them makes chunks seen
    // through a view finder on a sublevel render entities and block entities but no terrain.
    modImplementation("curse.maven:spotlights-or-something-1565038:8429512") // searchlights 1.3.1, Veil cone spotlights
    modImplementation("curse.maven:headlight-1190219:6282140") // 2.0.1
    // Needs Create + Architectury at runtime, and Create is compileOnly here, so this one stays off
    // the dev runtime until Create's runtime stack (flywheel/ponder/registrate) is declared too.
    modCompileOnly("curse.maven:create-train-lights-1318080:7761822") // v1.1.1
    modImplementation("maven.modrinth:flare-guns:1.0.0")
    // Create contraption view-finder integration (platform impl: CreatePlatStuffImpl)
    modCompileOnly("curse.maven:create-328085:7963363")
    modCompileOnly("curse.maven:create-aeronautics-676721:8240058") // 1.3.0, the newest 1.21.1 build
    api("dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:[${sable_companion_version},)")
    jarJar("dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:[${sable_companion_version},)")

}
