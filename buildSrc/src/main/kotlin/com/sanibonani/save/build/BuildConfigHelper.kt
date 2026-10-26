package com.sanibonani.save.build

import org.gradle.api.Project
import java.util.Properties

fun getSafeProp(project: Project, key: String, default: String = ""): String {
    val localProps = Properties()
    val f = project.rootProject.file("local.properties")
    if (f.exists()) localProps.load(f.inputStream())
    val raw = localProps.getProperty(key, default)
    val clean = raw.trim().removeSurrounding("\"")
    return "\"$clean\""
}
