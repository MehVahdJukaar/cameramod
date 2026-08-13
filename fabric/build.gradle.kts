plugins {
    id("com.possible-triangle.fabric")
}


fabric {
    dependOn(project(":common"))
    accessWidener(project(":common"))
}

loom{

}


val moonlight_version: String by extra
val supplementaries_version: String by extra
val mixin_squared_version: String by extra
val sable_companion_version: String by extra
val codecui_version: String by extra

dependencies {

    modImplementation("net.mehvahdjukaar:moonlight-fabric:${moonlight_version}")

    modRuntimeOnly("net.mehvahdjukaar:codecui-fabric:${codecui_version}")

    include("com.github.bawnorton.mixinsquared:mixinsquared-fabric:${mixin_squared_version}")
    implementation("com.github.bawnorton.mixinsquared:mixinsquared-fabric:${mixin_squared_version}")
    annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-fabric:${mixin_squared_version}")


    modCompileOnly("curse.maven:supplementaries-412082:8044264")
            //modImplementation("net.mehvahdjukaar:supplementaries-fabric:${supplementaries_version}")
    modCompileOnly("curse.maven:watermedia-869524:7072353")

    modCompileOnly("curse.maven:irisshaders-455508:6213635")
    modCompileOnly("curse.maven:exposure-871755:8223556")
    // Joy of Painting (xercapaint) is Fabric-only on 1.21.1, so the integration lives in :fabric
    modImplementation("maven.modrinth:joy-of-painting:1.21.1-2.0.1")
    modCompileOnly("curse.maven:distant-horizons-508933:8287411")
    modCompileOnly("curse.maven:sodium-394468:8382327")
    modCompileOnly("curse.maven:cc-tweaked-282001:5714511")
    modCompileOnly("curse.maven:jei-238222:7420583")
    modRuntimeOnly("maven.modrinth:jade:15.10.5+fabric")
    modCompileOnly("maven.modrinth:modmenu:4.0.6")
    modCompileOnly("maven.modrinth:flashback:0.32.0")
    modCompileOnly("curse.maven:geckolib-388172:7707138")

    modImplementation("curse.maven:refurbished-furniture-897116:7473562")
    modImplementation("curse.maven:framework-549225:7462474")

    // Vampirism has no Fabric version
    modCompileOnly("curse.maven:origins-391943:7365871")

    modCompileOnly("foundry.veil:veil-neoforge-1.21.1:4.4.1")

   // modRuntimeOnly("foundry.veil:veil-fabric-1.21.1:4.0.0")
   // modRuntimeOnly("curse.maven:fsable-1312371:8007004")
    modApi("dev.ryanhcode.sable-companion:sable-companion-fabric-1.21.1:${sable_companion_version}")
    include("dev.ryanhcode.sable-companion:sable-companion-fabric-1.21.1:${sable_companion_version}")

    runtimeOnly("org.anarres:jcpp:1.4.14")
    modImplementation("io.github.douira:glsl-transformer:2.0.1")
    modCompileOnly("curse.maven:simple-clouds-1121215:6928979")

    // Create contraption view-finder integration disabled on Fabric: Create Fabric has no build for this
    // Minecraft version (latest release is for 1.20.1). See CompatHandler.CREATE.
    //modCompileOnly("curse.maven:create-fabric-624165:7286603")

    // modImplementation("cc.tweaked-cobalt:cobalt:0.93")

}
