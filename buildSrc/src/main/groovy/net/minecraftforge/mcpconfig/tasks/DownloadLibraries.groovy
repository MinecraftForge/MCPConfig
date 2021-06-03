package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*
import de.undercouch.gradle.tasks.download.DownloadAction

public class DownloadLibraries extends DefaultTask {
    @InputFile json
    @Input config
    @Input dest
    
    @TaskAction
    def exec() {
        Utils.init()
        
        json.json.libraries.each{ lib ->
            def artifacts = (lib.downloads.artifact == null ? [] : [lib.downloads.artifact]) + lib.downloads.get('classifiers', [:]).values()
            artifacts.each{ art ->
                download(art.url, new File(dest, art.path))
            }
        }
        for (def side : ['client', 'server', 'joined']) {
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
        def ret = new DownloadAction(project, this)
        ret.overwrite(false)
        ret.useETag('all')
        ret.src url
        ret.dest target
        ret.execute()
    }
}