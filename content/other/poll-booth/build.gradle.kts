plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.db)
    implementation(projects.api.dbGateway)
    implementation(projects.api.pluginCommons)
    implementation(projects.engine.game)
}
