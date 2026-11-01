plugins {
    id("com.possible-triangle.common")
}

common {
    neoformVersion = "1.21.1-20240808.144430"
    accessWidener()
}

val moonlight_version: String by extra
val supplementaries_version: String by extra
val candlelight_version: String by extra
val mixin_squared_version: String by extra
val sable_companion_version: String by extra

dependencies {

    modCompileOnly("net.mehvahdjukaar:moonlight-common:${moonlight_version}")
    modApi("dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:[${sable_companion_version},)")
    accessTransformers("net.mehvahdjukaar:moonlight-common:${moonlight_version}")


    implementation("com.github.bawnorton.mixinsquared:mixinsquared-common:${mixin_squared_version}")
    annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-common:${mixin_squared_version}")


    //modImplementation("net.mehvahdjukaar:supplementaries-neoforge:${supplementaries_version}")
    modImplementation("curse.maven:supplementaries-412082:8044262")


    modCompileOnly("curse.maven:exposure-871755:8223555")
    modCompileOnly("maven.modrinth:joy-of-painting:73U02HbA")
    modCompileOnly("curse.maven:distant-horizons-508933:8908517")
    modCompileOnly("maven.modrinth:iris:1.8.8+1.21.1-neoforge")

    modCompileOnly("curse.maven:cc-tweaked-282001:5714512")
    //modCompileOnly files("mods/entityculling-fabric-1.10.1-mc1.21.1.jar")
    //modCompileOnly("maven.modrinth:entityculling:1.10.1-1.21.1+-+NeoForge")
    //modCompileOnly "curse.maven:entityculling-448233:7900085"
    modCompileOnly("curse.maven:jei-238222:7420587")
    modCompileOnly("maven.modrinth:jade:15.10.5+neoforge")
    //modCompileOnly("curse.maven:irisshaders-455508:6213635")
    modCompileOnly("curse.maven:sodium-394468:6382651")
    modCompileOnly("curse.maven:geckolib-388172:7707149")

    modCompileOnly("curse.maven:refurbished-furniture-897116:7473565")
    modCompileOnly("curse.maven:framework-549225:7462477")

    modCompileOnly("curse.maven:alexs-caves-924854:4806837")
    modImplementation("maven.modrinth:watermedia:3.0.0.23")

    modCompileOnly("foundry.veil:veil-neoforge-1.21.1:4.4.1")

    modImplementation("maven.modrinth:flare-guns:1.0.0")

    modCompileOnly("curse.maven:simple-clouds-1121215:6928979")


}
