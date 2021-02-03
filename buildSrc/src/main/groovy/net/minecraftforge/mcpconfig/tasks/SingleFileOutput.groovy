package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*

//Small helper base task that only outputs one file, this is useful so I can jsut do task.dest instead of task.outputs.files.singleFile 
class SingleFileOutput extends DefaultTask {
    @OutputFile File dest //dest is used to be consistant with the download plugin's Download task
}