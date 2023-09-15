package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import de.undercouch.gradle.tasks.download.DownloadAction

import javax.inject.Inject

abstract class DownloadLibraries extends DefaultTask {
    @InputFile json
    @Input config
    @InputDirectory File dest

    @Inject
    protected abstract ProjectLayout getProjectLayout()

    @Inject
    protected abstract ObjectFactory getObjectFactory()

    @TaskAction
    void exec() {
        Utils.init()
        
        json.json.libraries.each{ lib ->
            def artifacts = (lib.downloads.artifact == null ? [] : [lib.downloads.artifact]) + lib.downloads.get('classifiers', [:]).values()
            artifacts.each{ art ->
                download(art.url, new File(dest, art.path))
            }
        }
        for (String side in ['client', 'server', 'joined']) {
            if (config?.libraries?.get(side) != null) {
                config.libraries.get(side).each { art ->
                    download(
                        'https://maven.minecraftforge.net/' + art.toMavenPath(),
                        new File(dest, art.toMavenPath())
                    )
                }
            }
        }
    }
    
    def download(def url, def target) {
        var constructor = DownloadAction.class.getDeclaredConstructor(ProjectLayout, Logger, Object, ObjectFactory, boolean, File)
        constructor.setAccessible(true)
        var ret = (DownloadAction) constructor.newInstance(projectLayout, this.logger, this, objectFactory, false, projectLayout.buildDirectory.getAsFile().get())
        ret.overwrite(false)
        ret.useETag('all')
        ret.src url
        ret.dest target
        ret.execute()
    }
}
